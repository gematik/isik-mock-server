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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class MediaTypeValidator {
	private static final String FHIR_VERSION = "fhirVersion";

	public boolean validateAcceptHeader(String acceptHeader, HttpServletResponse response) throws IOException {
		if (acceptHeader == null || acceptHeader.isEmpty()) return true;

		if (hasWrongFhirVersion(acceptHeader)) {
			String message = "Unsupported FHIR version";
			log.error(message);
			response.sendError(406, message);
			return false;
		}

		List<MediaType> acceptMediaTypes = MediaType.parseMediaTypes(acceptHeader);
		boolean hasSupportedFormat = acceptMediaTypes.stream()
				.anyMatch(mt -> isSupportedMediaType(mt)
						&& (mt.getParameter(FHIR_VERSION) == null
								|| Objects.equals(mt.getParameter(FHIR_VERSION), "4.0")));
		if (!hasSupportedFormat) {
			String message = "Unsupported media type in Accept header: '"
					+ acceptHeader
					+ "'. Supported media types: application/fhir+json, application/fhir+xml";
			log.error(message);
			response.sendError(406, message);
			return false;
		}

		return true;
	}

	public boolean validateContentTypeHeader(String contentTypeHeader, HttpServletResponse response)
			throws IOException {
		if (contentTypeHeader == null || contentTypeHeader.isEmpty()) {
			String message = "Content-Type header is required";
			log.error(message);
			response.sendError(400, message);
			return false;
		}

		if (hasWrongFhirVersion(contentTypeHeader)) {
			String message = "Unsupported FHIR version";
			log.error(message);
			response.sendError(406, message);
			return false;
		}

		MediaType contentType = MediaType.parseMediaType(contentTypeHeader);
		boolean hasSupportedFormat = isSupportedMediaType(contentType)
				&& (contentType.getParameter(FHIR_VERSION) == null
						|| Objects.equals(contentType.getParameter(FHIR_VERSION), "4.0"));
		if (!hasSupportedFormat) {
			String message = "Unsupported media type in Content-Type header: "
					+ contentType
					+ " Supported media types: application/fhir+json, application/fhir+xml";
			log.error(message);
			response.sendError(415, message);
			return false;
		}

		return true;
	}

	private boolean hasWrongFhirVersion(String contentTypeHeader) {
		List<MediaType> mediaTypes = MediaType.parseMediaTypes(contentTypeHeader);
		return mediaTypes.stream()
				.anyMatch(mt -> isSupportedMediaType(mt)
						&& mt.getParameter(FHIR_VERSION) != null
						&& !Objects.equals(mt.getParameter(FHIR_VERSION), "4.0"));
	}

	private boolean isSupportedMediaType(MediaType mediaType) {
		return mediaType.isWildcardType()
				|| ("application".equals(mediaType.getType())
						&& ("fhir+xml".equals(mediaType.getSubtype()) || "fhir+json".equals(mediaType.getSubtype())));
	}
}
