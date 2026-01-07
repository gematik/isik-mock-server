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
import ca.uhn.fhir.rest.api.EncodingEnum;
import lombok.SneakyThrows;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class)
@ActiveProfiles("integrationtest")
@Execution(ExecutionMode.CONCURRENT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FhirValidationIT {

	private static final String DIR = "integration-tests";

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private final FhirContext fhirContext = FhirContext.forR4();

	private String getServerUrl() {
		return String.format("http://localhost:%s/fhir/", port);
	}

	@TestFactory
	@SneakyThrows
	Stream<DynamicTest> testPOSTValidation() {
		return Files.walk(Paths.get(String.format("src/test/resources/%s", DIR)))
				.filter(path -> path.toString().endsWith(".json") || path.toString().endsWith(".xml"))
				.map(file -> DynamicTest.dynamicTest(file.getParent().getFileName() + " resource: " + file.getFileName().toString(),
						() -> testPOSTResource(file)));
	}

	@SneakyThrows
	void testPOSTResource(Path path) {
		String body = Files.readString(path);

		IParser parser = EncodingEnum.detectEncoding(body).newParser(fhirContext);

		IBaseResource resource = parser.parseResource(body);
		String resourceType = resource.fhirType();

		String url = String.format("%s/%s", getServerUrl(), resourceType);

		HttpHeaders headers = new HttpHeaders();
		String headerValue = path.toString().endsWith(".json") ? "application/fhir+json" : "application/fhir+xml";
		headers.add("Content-Type", headerValue);
		headers.add("Accept", headerValue);
		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

		ResponseEntity<String> response = restTemplate.exchange(
				url,
				HttpMethod.POST,
				requestEntity,
				String.class
		);

		boolean isValidResource = isValidResourceFolder(path);
		assertThat(response.getStatusCode().value()).isEqualTo(isValidResource ? 201 : 400);
	}

	public boolean isValidResourceFolder(Path path) {
		Path current = path;
		do {
			if (current.getFileName().toString().equals("valid"))
				return true;

			current = current.getParent();
		} while (current != null);

		return false;
	}
}
