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

		final String httpMethod = theRequest.getMethod();
		// process only POST and PUT requests
		if (!("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod))) {
			return true;
		}

		final String pathInfo = theRequest.getPathInfo();
		// Skip validation for FHIR operation calls (e.g., $book, $generate-metadata) or _search.
		// Operations define their own input format and handle validation internally;
		// nested resources in Parameters may be intentionally incomplete.
		// FIXME: Skip Subscription resources: the backport-subscription profile is not properly loaded
		// from the Reference Validator Library with the ISiK 5 Plugin.
		if (pathInfo != null
				&& (pathInfo.contains("/$") || pathInfo.contains("/_search") || pathInfo.startsWith("/Subscription"))) {
			return true;
		}

		if (StringUtils.isEmpty(pathInfo) || pathInfo.startsWith("/")) {
			final String body = ((ReusableRequestWrapper) theRequest).getBody();
			final EncodingEnum encoding = EncodingEnum.detectEncoding(body);
			final IParser parser = encoding.newParser(ctx);

			final IBaseResource resource = parser.parseResource(body);
			final ValidationResult validationResult = validationHandler.validateResource(resource, body);
			final ValidationResult filteredResult = ValidationResultFilter.filter(validationResult);

			if (!filteredResult.isValid()) {
				final OperationOutcome result =
						new ValidationResultToOperationOutcomeConverter(ctx).toOperationOutcome(filteredResult);
				ResponseUtils.sendValidationErrorResponse(
						theResponse,
						400,
						result,
						"The FHIR resource inside the request is invalid. It will not be saved.",
						parser,
						encoding);

				return false;
			}
		}

		return true;
	}
}
