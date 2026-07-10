package de.gematik.isik.mockserver.smart;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that serves the SMART on FHIR well-known discovery document for requests to {@code
 * /fhir/.well-known/smart-configuration}.
 *
 * <p>The HAPI FHIR server is registered as a separate servlet at {@code /fhir/*}, which takes
 * precedence over the Spring MVC {@link org.springframework.web.servlet.DispatcherServlet} for all
 * paths under {@code /fhir/}. Because HAPI FHIR does not natively serve the SMART well-known
 * document, requests to {@code /fhir/.well-known/smart-configuration} would otherwise result in an
 * HTTP 404 from the FHIR servlet.
 *
 * <p>This filter intercepts the request BEFORE servlet dispatch and writes the discovery JSON
 * directly to the response, bypassing the FHIR servlet entirely for this specific path.
 *
 * <p>CORS headers (permissive for a mock server) are also added so that SMART clients served from
 * any origin can fetch the document.
 *
 * <p>Only active when {@code spring.security.oauth2.enable=true}.
 */
@ConditionalOnProperty(
		name = {"spring.security.oauth2.enable"},
		havingValue = "true")
@Slf4j
@Component
@Order(Integer.MIN_VALUE)
public class SmartWellKnownFilter extends OncePerRequestFilter {

	static final String WELL_KNOWN_PATH = "/fhir/.well-known/smart-configuration";

	private final SmartConfigController smartConfigController;
	private final ObjectMapper objectMapper;

	public SmartWellKnownFilter(final SmartConfigController smartConfigController, final ObjectMapper objectMapper) {
		this.smartConfigController = smartConfigController;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
			final HttpServletRequest request,
			final @NonNull HttpServletResponse response,
			final @NonNull FilterChain filterChain)
			throws ServletException, IOException {

		String path = request.getRequestURI();
		// Strip context path if present (e.g., /app/fhir/... → /fhir/...).
		String contextPath = request.getContextPath();
		if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
			path = path.substring(contextPath.length());
		}

		if (WELL_KNOWN_PATH.equals(path)) {
			// Always add CORS headers for this public discovery document.
			response.setHeader("Access-Control-Allow-Origin", "*");
			response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
			response.setHeader("Access-Control-Allow-Headers", "*");

			// Handle CORS preflight OPTIONS request.
			if (HttpMethod.OPTIONS.matches(request.getMethod())) {
				response.setStatus(HttpServletResponse.SC_NO_CONTENT);
				return;
			}

			if (HttpMethod.GET.matches(request.getMethod())) {
				log.debug("Intercepting SMART well-known request for path: {}", path);

				response.setStatus(HttpServletResponse.SC_OK);
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.setCharacterEncoding("UTF-8");

				objectMapper.writeValue(response.getWriter(), smartConfigController.config());
				return; // Do NOT continue the filter chain — response is complete.
			}
		}

		filterChain.doFilter(request, response);
	}
}
