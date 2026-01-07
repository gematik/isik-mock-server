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

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Interceptor
@Component
@RequiredArgsConstructor
public class MediaTypeInterceptor {

	@Autowired
	private final MediaTypeValidator validator;

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
	public boolean incomingRequestPreProcessed(
			final HttpServletRequest theRequest, final HttpServletResponse theResponse) throws IOException {
		if (theRequest.getPathInfo() != null
				&& java.util.Arrays.asList(theRequest.getPathInfo().split("/")).contains("Binary")) {
			log.debug("Skipping media type validation for Binary resource");
			return true;
		}
		String acceptHeader = theRequest.getHeader("Accept");
		if (!validator.validateAcceptHeader(acceptHeader, theResponse)) {
			return false;
		}

		String method = theRequest.getMethod();
		if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
			String contentTypeHeader = theRequest.getHeader("Content-Type");
			return validator.validateContentTypeHeader(contentTypeHeader, theResponse);
		}

		return true;
	}
}
