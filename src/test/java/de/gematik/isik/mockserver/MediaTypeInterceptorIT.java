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

import ca.uhn.fhir.jpa.starter.Application;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static de.gematik.isik.mockserver.helper.ResourceLoadingHelper.loadResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = Application.class
)
@ActiveProfiles("integrationtest")
class MediaTypeInterceptorIT {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private String baseUrl() {
		return String.format("http://localhost:%d/fhir/", port);
	}

	@ParameterizedTest(name = "GET /metadata – Accept=\"{0}\" → HTTP {1}")
	@CsvSource({
			// unsupported media type
			"application/xml, 406",
			// supported type but wrong FHIR version
			"application/fhir+json;fhirVersion=3.0, 406",
			// supported with no version → ok
			"application/fhir+json, 200",
			// supported XML
			"application/fhir+xml, 200",
			// supported JSON + explicit correct version
			"application/fhir+json;fhirVersion=4.0, 200"
	})
	void getMetadata_AcceptHeaderValidation(String acceptHeader, int expectedStatus) {
		HttpHeaders headers = new HttpHeaders();
		if (!acceptHeader.isEmpty()) {
			headers.add(HttpHeaders.ACCEPT, acceptHeader);
		}
		HttpEntity<Void> request = new HttpEntity<>(headers);

		ResponseEntity<String> response = restTemplate.exchange(
				baseUrl() + "/metadata",
				HttpMethod.GET,
				request,
				String.class
		);

		assertThat(response.getStatusCode().value())
				.as("GET /metadata with Accept=%s should be %d", acceptHeader, expectedStatus)
				.isEqualTo(expectedStatus);
	}

	@ParameterizedTest(name = "POST /Slot – Accept=\"{0}\", Content-Type=\"{1}\" → HTTP {2}")
	@CsvSource({
			// invalid Accept
			"application/xml, application/fhir+json, 406",
			// valid Accept but unsupported Content-Type
			"application/fhir+json, application/json, 415",
			// valid Accept but wrong FHIR version in Content-Type
			"application/fhir+json, application/fhir+json;fhirVersion=3.0, 406",
			// both headers valid → Created
			"application/fhir+json, application/fhir+json, 201"
	})
	void postSlot_MediaTypeValidation(String accept, String contentType, int expectedStatus) {
		String slotJson = loadResourceAsString("fhir-examples/valid/Slot-Free-Block-Example.json");

		HttpHeaders headers = new HttpHeaders();
		if (!accept.isEmpty()) {
			headers.add(HttpHeaders.ACCEPT, accept);
		}
		if (!contentType.isEmpty()) {
			headers.add(HttpHeaders.CONTENT_TYPE, contentType);
		}
		HttpEntity<String> request = new HttpEntity<>(slotJson, headers);

		ResponseEntity<String> response = restTemplate.exchange(
				baseUrl() + "/Slot",
				HttpMethod.POST,
				request,
				String.class
		);

		assertThat(response.getStatusCode().value())
				.as("POST /Slot Accept=%s, Content-Type=%s should be %d", accept, contentType, expectedStatus)
				.isEqualTo(expectedStatus);
	}
}
