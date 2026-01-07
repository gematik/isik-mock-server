package de.gematik.isik.mockserver.operation;

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
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import de.gematik.isik.mockserver.async.AsyncAppointmentBookJobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AppointmentBookOperation implements IResourceProvider {

	@Autowired
	private FhirContext ctx;

	@Autowired
	private AppointmentBookHandler appointmentBookHandler;

	@Autowired
	private AsyncAppointmentBookJobService asyncAppointmentBookJobService;

	@Operation(name = "book", manualResponse = true, manualRequest = true)
	@SneakyThrows
	public Appointment bookAppointment(
			HttpServletRequest theRequest, HttpServletResponse theResponse, RequestDetails theRequestDetails) {
		log.info("Incoming Appointment/$book operation...");
		final String body = theRequest.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

		if (!body.isBlank()) {
			final String preferHeader = theRequest.getHeader("Prefer");
			if (preferHeader != null && preferHeader.contains("respond-async")) {
				String jobId = UUID.randomUUID().toString();

				CompletableFuture<AppointmentHandlerReturnObject> futureResult =
						appointmentBookHandler.handleIncomingAppointmentAsync(body, theRequestDetails);

				asyncAppointmentBookJobService.submitJob(jobId, futureResult);

				String contentLocationUrl = buildContentLocationUrl(theRequest, jobId);
				theResponse.setHeader("Content-Location", contentLocationUrl);
				theResponse.setStatus(HttpServletResponse.SC_ACCEPTED);
				return null;
			}

			try {
				theResponse.setContentType("application/json");
				log.debug("Incoming Appointment: {}", body);
				final AppointmentHandlerReturnObject returnObject =
						appointmentBookHandler.handleIncomingAppointment(body, theRequestDetails);
				if (returnObject.isOperationSuccessful()) {
					log.info(
							"Appointment successfully created. ID: {}",
							returnObject.getAppointment().getId());
					String createdAppointment =
							ctx.newJsonParser().encodeResourceToString(returnObject.getAppointment());
					log.debug("Response Appointment: {}", createdAppointment);
					theResponse.getWriter().print(createdAppointment);
					theResponse.setStatus(HttpServletResponse.SC_CREATED);
				} else {
					log.info("Error in processing the incoming appointment");
					OperationOutcome operationOutcome = returnObject.getOperationOutcome();
					if (operationOutcome == null) {
						throw new NullPointerException("AppointmentHandlerReturnObject has no OperationOutcome");
					}
					theResponse.getWriter().print(ctx.newJsonParser().encodeResourceToString(operationOutcome));
					theResponse.setStatus(400);
				}
			} catch (final PreconditionFailedException e) {
				log.info(e.getMessage());
				theResponse.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
				final OperationOutcome outcome = new OperationOutcome();
				outcome.addIssue()
						.setSeverity(OperationOutcome.IssueSeverity.FATAL)
						.setDiagnostics(e.getMessage());
				theResponse.getWriter().print(ctx.newJsonParser().encodeResourceToString(outcome));
			}
		}
		return null;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Appointment.class;
	}

	private String buildContentLocationUrl(HttpServletRequest request, String jobId) {
		String requestUrl = request.getRequestURL().toString();
		return requestUrl.replace("/fhir/Appointment/$book", "/async-jobs/" + jobId);
	}
}
