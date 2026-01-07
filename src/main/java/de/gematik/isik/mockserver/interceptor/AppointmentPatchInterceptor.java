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
import ca.uhn.fhir.rest.api.server.RequestDetails;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import de.gematik.isik.mockserver.helper.ResponseUtils;
import de.gematik.isik.mockserver.helper.ReusableRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Interceptor
@Component
@RequiredArgsConstructor
public class AppointmentPatchInterceptor {

	private final AppointmentPatchHandler appointmentPatchHandler;
	private final FhirContext ctx;

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean incomingRequestPostProcessed(
			final HttpServletRequest theRequest,
			final HttpServletResponse theResponse,
			final RequestDetails theRequestDetails)
			throws IOException {
		String httpMethod = theRequest.getMethod();
		if (!"PATCH".equalsIgnoreCase(httpMethod)) {
			return true;
		}

		String pathInfo = theRequest.getPathInfo();
		if (pathInfo.matches("^/Appointment/[^/]+$")) {
			log.info("Incoming Appointment PATCH...");
			String body = ((ReusableRequestWrapper) theRequest).getBody();
			EncodingEnum encoding = EncodingEnum.detectEncoding(body);
			IParser parser = encoding.newParser(ctx);

			IBaseResource resource = parser.parseResource(body);
			if (resource instanceof Parameters updateAppointmentParameters) {
				OperationOutcome result =
						appointmentPatchHandler.handle(updateAppointmentParameters, theRequestDetails);

				if (OperationOutcomeUtils.hasErrorIssue(result)) {
					ResponseUtils.sendValidationErrorResponse(
							theResponse,
							400,
							result,
							"The PATCH Parameters for the specified Appointment are invalid. The PATCH won`t be applied.",
							parser,
							encoding);
					return false;
				}
			} else {
				OperationOutcome outcome = new OperationOutcome();
				outcome.addIssue()
						.setSeverity(OperationOutcome.IssueSeverity.ERROR)
						.setDiagnostics(String.format(
								"Wrong ResourceType in request body: '%s'. Request body must be a Parameters resource for PATCH requests.",
								resource.getIdElement().getResourceType()));
				ResponseUtils.sendValidationErrorResponse(
						theResponse, 400, outcome, "Invalid request body for PATCH operation.", parser, encoding);
				return false;
			}
		}
		return true;
	}
}
