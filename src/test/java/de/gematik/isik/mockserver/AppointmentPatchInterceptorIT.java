package de.gematik.isik.mockserver;

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
import ca.uhn.fhir.jpa.starter.Application;
import org.hl7.fhir.r4.model.Appointment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static de.gematik.isik.mockserver.helper.ResourceLoadingHelper.loadResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class)
@ActiveProfiles("integrationtest")
class AppointmentPatchInterceptorIT {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private String getServerUrl() {
		return String.format("http://localhost:%s/fhir/", port);
	}

	@ParameterizedTest
	@ValueSource(strings = {"fhir-examples/valid/valid-patch-appointment-parameters.json", "fhir-examples/invalid/invalid-patch-appointment-parameters.json"})
	void testIncomingAppointmentPatchParameters(String input) {
		String body = loadResourceAsString(input);
		String url = String.format("%s/Appointment/Booking-Example", getServerUrl());

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/fhir+json");
		headers.add("Accept", "application/fhir+json");
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

		ResponseEntity<String> response = restTemplate.exchange(
				url,
				HttpMethod.PATCH,
				requestEntity,
				String.class
		);

		boolean isValid = !input.contains("invalid");
		assertThat(response.getStatusCode().value()).isEqualTo(isValid ? 200 : 400);
		if (isValid) {
			Appointment responseAppointment = (Appointment) FhirContext.forR4().newJsonParser().parseResource(response.getBody());
			assertThat(responseAppointment.getStatus()).isEqualTo(Appointment.AppointmentStatus.CANCELLED);
		}
	}

	@Test
	void testWrongResourceTypeInRequestBody() {
		String body = loadResourceAsString("fhir-examples/valid/Slot-Free-Block-Example.json");
		String url = String.format("%s/Appointment/Booking-Example", getServerUrl());

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/fhir+json");
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

		ResponseEntity<String> response = restTemplate.exchange(
				url,
				HttpMethod.PATCH,
				requestEntity,
				String.class
		);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains("Wrong ResourceType in request body: 'Slot'. Request body must be a Parameters resource for PATCH requests.");
	}
}
