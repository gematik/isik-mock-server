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
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncAppointmentBookJobControllerTest {

	@Mock
	private AsyncAppointmentBookJobService asyncAppointmentBookJobService;

	private final FhirContext fhirContext = FhirContext.forR4();

	@InjectMocks
	private AsyncAppointmentBookJobController controller;

	@Test
	void shouldReturnNotFoundWhenJobDoesNotExist() {
		String jobId = "nonExistingJob";
		when(asyncAppointmentBookJobService.getJob(jobId)).thenReturn(Optional.empty());

		ResponseEntity<String> response = controller.getJobResult(jobId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNull();
	}

	@Test
	void shouldReturnAcceptedWhenJobIsNotYetDone() {
		String jobId = "incompleteJob";
		CompletableFuture<AppointmentHandlerReturnObject> future = new CompletableFuture<>();
		// Note: The future is not completed
		when(asyncAppointmentBookJobService.getJob(jobId)).thenReturn(Optional.of(future));

		ResponseEntity<String> response = controller.getJobResult(jobId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(response.getBody()).isNull();
	}

	@Test
	void shouldReturnCreatedWithAppointmentJsonWhenOperationIsSuccessful() {
		String jobId = "successfulJob";
		AppointmentHandlerReturnObject result = mock(AppointmentHandlerReturnObject.class);
		when(result.isOperationSuccessful()).thenReturn(true);
		Appointment appointment = new Appointment();
		appointment.setId("1");
		when(result.getAppointment()).thenReturn(appointment);

		CompletableFuture<AppointmentHandlerReturnObject> future = CompletableFuture.completedFuture(result);
		when(asyncAppointmentBookJobService.getJob(jobId)).thenReturn(Optional.of(future));

		ResponseEntity<String> response = controller.getJobResult(jobId);

		String expectedJson = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(appointment);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isEqualTo(expectedJson);
	}

	@Test
	void shouldReturnBadRequestWithOperationOutcomeJsonWhenOperationIsNotSuccessful() {
		String jobId = "failedJob";
		AppointmentHandlerReturnObject result = mock(AppointmentHandlerReturnObject.class);
		when(result.isOperationSuccessful()).thenReturn(false);
		OperationOutcome outcome = new OperationOutcome();
		outcome.addIssue().setDiagnostics("Some error occurred");
		when(result.getOperationOutcome()).thenReturn(outcome);

		CompletableFuture<AppointmentHandlerReturnObject> future = CompletableFuture.completedFuture(result);
		when(asyncAppointmentBookJobService.getJob(jobId)).thenReturn(Optional.of(future));

		ResponseEntity<String> response = controller.getJobResult(jobId);

		String expectedJson = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isEqualTo(expectedJson);
	}

	@Test
	@SneakyThrows
	void shouldReturnInternalServerErrorWhenFutureGetThrowsGenericException() {
		String jobId = "exceptionJob";
		CompletableFuture<AppointmentHandlerReturnObject> future = mock(CompletableFuture.class);
		when(future.isDone()).thenReturn(true);
		when(future.get()).thenThrow(new RuntimeException("Test exception"));
		when(asyncAppointmentBookJobService.getJob(jobId)).thenReturn(Optional.of(future));

		ResponseEntity<String> response = controller.getJobResult(jobId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody())
			.contains("Internal error while processing the asynchronous job")
			.contains(jobId)
			.contains("Test exception");
	}

	@Test
	@SneakyThrows
	void shouldReturnInternalServerErrorWithInterruptedMessageWhenFutureGetThrowsInterruptedException() {
		String jobId = "interruptedJob";
		CompletableFuture<AppointmentHandlerReturnObject> future = mock(CompletableFuture.class);
		when(future.isDone()).thenReturn(true);
		when(future.get()).thenThrow(new InterruptedException("Interrupted"));
		when(asyncAppointmentBookJobService.getJob(jobId)).thenReturn(Optional.of(future));

		ResponseEntity<String> response = controller.getJobResult(jobId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody())
			.isEqualTo(String.format("The async job with id '%s' was interrupted.", jobId));
	}
}
