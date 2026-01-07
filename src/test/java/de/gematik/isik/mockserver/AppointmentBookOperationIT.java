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
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
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
class AppointmentBookOperationIT {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private final IParser parser = FhirContext.forR4().newJsonParser();

	private String getServerUrl() {
		return String.format("http://localhost:%s/fhir/", port);
	}

	@ParameterizedTest
	@ValueSource(strings = {"fhir-examples/valid/valid-appointment.json", "fhir-examples/invalid/invalid-appointment-with-start-and-end.json"})
	void testBookAppointment(String input) {
		String body = loadResourceAsString(input);
		String url = String.format("%s/Appointment/$book", getServerUrl());

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/fhir+json");
		headers.add("Accept", "application/fhir+json");
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

		ResponseEntity<String> response = restTemplate.exchange(
				url,
				HttpMethod.POST,
				requestEntity,
				String.class
		);

		boolean isValid = !input.contains("invalid");
		assertThat(response.getStatusCode().value()).isEqualTo(isValid ? 201 : 400);
		if(!isValid) {
			assertThat(response.getBody()).isNotNull();
			assertThat(response.getBody()).contains("The referenced Patient has 'active=false' but must be 'active=true'.");
		}
	}

	@Test
	void testOverlappingSlotAndAppointment() {
		String body = loadResourceAsString("fhir-examples/valid/valid-appointment-booking-parameters.json");
		String url = String.format("%s/Appointment/$book", getServerUrl());

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/fhir+json");
		headers.add("Accept", "application/fhir+json");
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

		ResponseEntity<String> response = restTemplate.exchange(
				url,
				HttpMethod.POST,
				requestEntity,
				String.class
		);
		assertThat(response.getStatusCode().value()).isEqualTo(201);

		String body1 = loadResourceAsString("fhir-examples/valid/appointment-book-parameters-with-overlapping-slot.json");
		HttpEntity<String> requestEntity1 = new HttpEntity<>(body1, headers);
		ResponseEntity<String> response1 = restTemplate.exchange(
				url,
				HttpMethod.POST,
				requestEntity1,
				String.class
		);

		assertThat(response1.getStatusCode().value()).isEqualTo(400);
		assertThat(response1.getBody()).contains(
				"Incoming Appointment: Start and end are overlapping with existing slots. " +
						"Incoming Appointment Start: Fri Jan 01 15:30:00 CET 2027, Incoming Appointment End: Fri Jan 01 15:59:00 CET 2027, " +
						"Overlapping Slots: \\n" +
						"Slot/Busy-Block/_history/1: Start: Fri Jan 01 15:00:00 CET 2027, End: Fri Jan 01 16:00:00 CET 2027");
	}

	@Test
	void testAppointmentRescheduling() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/fhir+json");
		headers.add("Accept", "application/fhir+json");
		String url = String.format("%s/Appointment/$book", getServerUrl());

		String cancelledAppointmentBody = loadResourceAsString("fhir-examples/valid/valid-appointment.json");
		HttpEntity<String> requestEntity1 = new HttpEntity<>(cancelledAppointmentBody, headers);

		ResponseEntity<String> response1 = restTemplate.exchange(
				url,
				HttpMethod.POST,
				requestEntity1,
				String.class
		);

		Appointment cancelledAppointment = (Appointment) parser.parseResource(response1.getBody());
		assertThat(cancelledAppointment.getStatus()).isEqualTo(Appointment.AppointmentStatus.BOOKED);

		String body = loadResourceAsString("fhir-examples/valid/valid-appointment-rescheduling-parameters.json");
		// Note:
		// "By default, HAPI will strip resource versions from references between resources.
		// For example, if you set a reference to Patient.managingOrganization to the value Patient/123/_history/2,
		// HAPI will encode this reference as Patient/123"
		// Source: https://hapifhir.io/hapi-fhir/docs/model/references.html#versioned-references
		String cancelledApptId = cancelledAppointment.getId().replace("/_history/1", "");
		body = body.replace("Appointment/CANCELLED_APPT_ID", cancelledApptId);
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

		ResponseEntity<String> response = restTemplate.exchange(
				url,
				HttpMethod.POST,
				requestEntity,
				String.class
		);
		assertThat(response.getStatusCode().value()).isEqualTo(201);

		Appointment newAppointment = (Appointment) parser.parseResource(response.getBody());
		Extension extension = newAppointment.getExtensionByUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-Appointment.replaces");
		assertThat(extension).isNotNull();
		Reference ref = (Reference) extension.getValue();
		assertThat(ref.getReference()).isEqualTo(cancelledApptId);

		HttpEntity<String> requestEntity2 = new HttpEntity<>(headers);
		url = String.format("%s/%s", getServerUrl(), cancelledApptId);

		ResponseEntity<String> response2 = restTemplate.exchange(
				url,
				HttpMethod.GET,
				requestEntity2,
				String.class
		);

		Appointment cancelledAppointmentAfterUpdate = (Appointment) parser.parseResource(response2.getBody());
		assertThat(cancelledAppointmentAfterUpdate.getStatus()).isEqualTo(Appointment.AppointmentStatus.CANCELLED);
	}

	@Test
	void testBookAppointmentAsynchronously() throws Exception {
		String body = loadResourceAsString("fhir-examples/valid/valid-appointment.json");
		String url = String.format("%s/Appointment/$book", getServerUrl());

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/fhir+json");
		headers.add("Accept", "application/fhir+json");
		headers.add("Prefer", "respond-async");
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(202);
		String contentLocation = response.getHeaders().getFirst("Content-Location");
		assertThat(contentLocation)
				.as("Content-Location header should be present for async jobs")
				.isNotNull()
				.contains("/async-jobs/");

		// Now poll the async job endpoint until it is completed.
		ResponseEntity<String> asyncResponse = null;
		int maxRetries = 20;
		int retry = 0;
		while (retry < maxRetries) {
			asyncResponse = restTemplate.getForEntity(contentLocation, String.class);
			if (asyncResponse.getStatusCode().value() != 202) {
				break;
			}
			Thread.sleep(500);
			retry++;
		}

		assertThat(asyncResponse).isNotNull();
		assertThat(asyncResponse.getStatusCode().value()).isEqualTo(201);

		Appointment appointment = parser.parseResource(Appointment.class, asyncResponse.getBody());
		assertThat(appointment).isNotNull();
		assertThat(appointment.getStatus()).isEqualTo(Appointment.AppointmentStatus.BOOKED);
	}
}
