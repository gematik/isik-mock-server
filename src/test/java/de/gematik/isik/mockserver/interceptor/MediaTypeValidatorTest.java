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

import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MediaTypeValidatorTest {

	@Mock
	private HttpServletResponse response;

	@InjectMocks
	private MediaTypeValidator validator;

	@Captor
	private ArgumentCaptor<Integer> statusCaptor;

	@Captor
	private ArgumentCaptor<String> messageCaptor;

	@ParameterizedTest
	@NullAndEmptySource
	@SneakyThrows
	void shouldNotRejectWhenAcceptHeaderIsNullOrEmpty(String header) {
		boolean result = validator.validateAcceptHeader(header, response);

		assertThat(result).isTrue();
		verifyNoInteractions(response);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"application/fhir+json;fhirVersion=3.0",
		"application/fhir+json;fhirVersion=3.0, application/fhir+xml"
	})
	@SneakyThrows
	void shouldRejectUnsupportedFhirVersionInAccept(String header) {
		boolean result = validator.validateAcceptHeader(header, response);

		assertThat(result).isFalse();
		verify(response).sendError(statusCaptor.capture(), messageCaptor.capture());
		assertThat(statusCaptor.getValue()).isEqualTo(406);
		assertThat(messageCaptor.getValue()).isEqualTo("Unsupported FHIR version");
	}

	@Test
	@SneakyThrows
	void shouldRejectWhenNoSupportedMediaTypeInAccept() {
		String header = "application/json";
		boolean result = validator.validateAcceptHeader(header, response);

		assertThat(result).isFalse();
		verify(response).sendError(statusCaptor.capture(), messageCaptor.capture());
		assertThat(statusCaptor.getValue()).isEqualTo(406);
		assertThat(messageCaptor.getValue()).isEqualTo(
			"Unsupported media type in Accept header: 'application/json'. Supported media types: application/fhir+json, application/fhir+xml"
		);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"*/*",
		"application/bla, */*",
		"application/fhir+json",
		"application/bla, application/fhir+json",
		"application/fhir+xml",
		"application/fhir+json;fhirVersion=4.0"
	})
	@SneakyThrows
	void shouldAcceptSupportedFormatsInAccept(String header) {
		boolean result = validator.validateAcceptHeader(header, response);

		assertThat(result).isTrue();
		verifyNoInteractions(response);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@SneakyThrows
	void shouldRejectWhenContentTypeHeaderIsNullOrEmpty(String header) {
		boolean result = validator.validateContentTypeHeader(header, response);

		assertThat(result).isFalse();
		verify(response).sendError(statusCaptor.capture(), messageCaptor.capture());
		assertThat(statusCaptor.getValue()).isEqualTo(400);
		assertThat(messageCaptor.getValue()).isEqualTo("Content-Type header is required");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"application/fhir+xml;fhirVersion=3.0",
		"application/fhir+xml;fhirVersion=3.0, application/fhir+json"
	})
	@SneakyThrows
	void shouldRejectUnsupportedFhirVersionInContentType(String header) {
		boolean result = validator.validateContentTypeHeader(header, response);

		assertThat(result).isFalse();
		verify(response).sendError(statusCaptor.capture(), messageCaptor.capture());
		assertThat(statusCaptor.getValue()).isEqualTo(406);
		assertThat(messageCaptor.getValue()).isEqualTo("Unsupported FHIR version");
	}

	@Test
	@SneakyThrows
	void shouldRejectUnsupportedMediaTypeInContentType() {
		String header = "application/json";
		boolean result = validator.validateContentTypeHeader(header, response);

		assertThat(result).isFalse();
		verify(response).sendError(statusCaptor.capture(), messageCaptor.capture());
		assertThat(statusCaptor.getValue()).isEqualTo(415);
		assertThat(messageCaptor.getValue()).isEqualTo(
			"Unsupported media type in Content-Type header: application/json Supported media types: application/fhir+json, application/fhir+xml"
		);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"application/fhir+json",
		"application/fhir+xml",
		"application/fhir+json;fhirVersion=4.0"
	})
	@SneakyThrows
	void shouldAcceptSupportedMediaTypesInContentType(String header) {
		boolean result = validator.validateContentTypeHeader(header, response);

		assertThat(result).isTrue();
		verifyNoInteractions(response);
	}
}
