package de.gematik.isik.mockserver.interceptor;

/*-
 * #%L
 * isik-mock-server
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes
 * by gematik, find details in the "Readme" file.
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import de.gematik.isik.mockserver.helper.ResponseUtils;
import de.gematik.isik.mockserver.helper.ReusableRequestWrapper;
import de.gematik.isik.mockserver.helper.ValidationResultFilter;
import de.gematik.refv.commons.exceptions.ValidationModuleInitializationException;
import de.gematik.refv.commons.validation.ValidationResult;
import de.gematik.refv.commons.validation.ValidationResultToOperationOutcomeConverter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Interceptor
@Component
@RequiredArgsConstructor
public class FhirValidationInterceptor {

	private final FhirValidationHandler validationHandler;
	private final FhirContext ctx;

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
	public boolean incomingRequestPreProcessed(
			final HttpServletRequest theRequest, final HttpServletResponse theResponse)
			throws IOException, ValidationModuleInitializationException {

		String httpMethod = theRequest.getMethod();
		if (!("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod))) {
			return true;
		}

		if (StringUtils.isEmpty(theRequest.getPathInfo())
				|| theRequest.getPathInfo().startsWith("/")) {
			String body = ((ReusableRequestWrapper) theRequest).getBody();
			EncodingEnum encoding = EncodingEnum.detectEncoding(body);
			IParser parser = encoding.newParser(ctx);

			IBaseResource resource = parser.parseResource(body);
			ValidationResult validationResult = validationHandler.validateResource(resource, body);
			ValidationResult filteredResult = ValidationResultFilter.filter(validationResult);

			setInvalidCodeIssuesAsWarning(filteredResult);

			if (!filteredResult.isValid()) {
				OperationOutcome result =
						new ValidationResultToOperationOutcomeConverter(ctx).toOperationOutcome(filteredResult);
				ResponseUtils.sendValidationErrorResponse(
						theResponse,
						400,
						result,
						"The fhir resource inside the request is invalid. It will not be saved.",
						parser,
						encoding);

				return false;
			}
		}

		return true;
	}

	void setInvalidCodeIssuesAsWarning(final ValidationResult validationResult) {
		validationResult.getValidationMessages().forEach(issue -> {
			if (issue.getMessage()
					.contains(
							"was not found in the value set 'ValueSet Klinische Dokumentenklassen-Liste (Version 2022)' (http://dvmd.de/fhir/ValueSet/kdl|2022)")) {
				issue.setSeverity(ResultSeverityEnum.WARNING);
			}
		});
	}
}
