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

import de.gematik.isik.mockserver.operation.AppointmentHandlerReturnObject;
import org.hl7.fhir.r4.model.Appointment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncAppointmentBookJobServiceTest {

	private AsyncAppointmentBookJobService service;

	@BeforeEach
	void setUp() {
		service = new AsyncAppointmentBookJobService();
	}

	@Test
	void shouldSubmitAndRetrieveJob() {
		String jobId = "job1";
		AppointmentHandlerReturnObject returnObject = new AppointmentHandlerReturnObject(new Appointment(), true, null);
		CompletableFuture<AppointmentHandlerReturnObject> future =
			CompletableFuture.completedFuture(returnObject);

		service.submitJob(jobId, future);
		Optional<CompletableFuture<AppointmentHandlerReturnObject>> retrievedJob = service.getJob(jobId);

		assertThat(retrievedJob)
			.as("Job should be present after submission")
			.isPresent()
			.as("Retrieved future should match the submitted one")
			.contains(future);
	}

	@Test
	void shouldReturnEmptyWhenJobNotFound() {
		String jobId = "nonExistingJob";
		Optional<CompletableFuture<AppointmentHandlerReturnObject>> retrievedJob = service.getJob(jobId);

		assertThat(retrievedJob)
			.as("No job should be found for a non-existing jobId")
			.isNotPresent();
	}
}
