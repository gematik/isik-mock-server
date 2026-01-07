package de.gematik.isik.mockserver.async;

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
import de.gematik.isik.mockserver.operation.AppointmentHandlerReturnObject;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class AsyncAppointmentBookJobController {

	@Autowired
	private AsyncAppointmentBookJobService asyncAppointmentBookJobService;

	@GetMapping("/async-jobs/{jobId}")
	public ResponseEntity<String> getJobResult(@PathVariable("jobId") String jobId) {
		FhirContext fhirContext = FhirContext.forR4();
		var optionalJob = asyncAppointmentBookJobService.getJob(jobId);
		if (optionalJob.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		CompletableFuture<AppointmentHandlerReturnObject> future = optionalJob.get();
		if (!future.isDone()) {
			// Note: The job is not finished yet
			return ResponseEntity.status(HttpStatus.ACCEPTED).build();
		}

		try {
			AppointmentHandlerReturnObject result = future.get();
			var parser = fhirContext.newJsonParser().setPrettyPrint(true);
			if (result.isOperationSuccessful()) {
				String jsonResponse = parser.encodeResourceToString(result.getAppointment());
				return ResponseEntity.status(HttpStatus.CREATED).body(jsonResponse);
			} else {
				String jsonResponse = parser.encodeResourceToString(result.getOperationOutcome());
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(jsonResponse);
			}
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body(String.format("The async job with id '%s' was interrupted.", jobId));
			}

			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue()
					.setDiagnostics(String.format(
							"Internal error while processing the asynchronous job with id '%s': %s",
							jobId, e.getMessage()));
			var parser = fhirContext.newJsonParser().setPrettyPrint(true);
			String jsonResponse = parser.encodeResourceToString(outcome);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(jsonResponse);
		}
	}
}
