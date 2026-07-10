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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Objects;

/**
 * Token Introspection Proxy — {@code POST /fhir/token/introspect}.
 *
 * <p>Proxies RFC 7662 token introspection requests to the Keycloak realm's introspection endpoint
 * ({@code {keycloakBaseUrl}/protocol/openid-connect/token/introspect}) and returns the response
 * unchanged. This allows downstream resource servers or SMART clients to perform active token
 * validation against the same authority that issued the token.
 *
 * <p>The endpoint is publicly accessible (no Bearer token required) because the caller provides the
 * client credentials in the {@code Authorization: Basic …} header as required by RFC 7662 section
 * 2.1.
 *
 * <p>Only active when {@code spring.security.oauth2.enable=true}.
 */
@ConditionalOnProperty(
		name = {"spring.security.oauth2.enable"},
		havingValue = "true")
@Slf4j
@RestController
public class SmartTokenIntrospectionController {
	private final SmartConfigProperties smartConfigProperties;
	private final RestTemplate restTemplate;

	public SmartTokenIntrospectionController(
			final SmartConfigProperties smartConfigProperties, final RestTemplate restTemplate) {
		this.smartConfigProperties = smartConfigProperties;
		this.restTemplate = restTemplate;
	}

	/**
	 * Proxies an RFC 7662 introspection request to the authorization server.
	 *
	 * <p>The caller must supply {@code Authorization: Basic <base64(clientId:clientSecret)>} and a
	 * {@code token} form parameter. The upstream Keycloak response is returned as-is.
	 *
	 * @param formData the URL-encoded form body (must contain {@code token})
	 * @param requestHeaders the original HTTP request headers (forwarded to Keycloak)
	 * @return the introspection response from Keycloak (HTTP 200 with RFC 7662 JSON body, or an error
	 *     status mirroring the upstream response)
	 */
	@SuppressWarnings("unchecked")
	@PostMapping(
			value = "/fhir/token/introspect",
			consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> introspect(
			@RequestBody final MultiValueMap<String, String> formData,
			@RequestHeader final HttpHeaders requestHeaders) {

		String introspectionUrl = smartConfigProperties.introspectionEndpoint();
		log.debug("Proxying token introspection request to {}", introspectionUrl);

		HttpHeaders forwardedHeaders = new HttpHeaders();
		forwardedHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		// Forward Authorization header (Basic credentials) if present.
		if (requestHeaders.containsKey(HttpHeaders.AUTHORIZATION)) {
			forwardedHeaders.put(
					HttpHeaders.AUTHORIZATION, Objects.requireNonNull(requestHeaders.get(HttpHeaders.AUTHORIZATION)));
		}

		HttpEntity<MultiValueMap<String, String>> proxyRequest = new HttpEntity<>(formData, forwardedHeaders);

		try {
			return (ResponseEntity<Map<String, Object>>)
					(ResponseEntity<?>) restTemplate.postForEntity(introspectionUrl, proxyRequest, Map.class);
		} catch (HttpStatusCodeException ex) {
			log.warn(
					"Upstream introspection endpoint returned {}: {}",
					ex.getStatusCode(),
					ex.getResponseBodyAsString());
			return ResponseEntity.status(ex.getStatusCode())
					.body(Map.of("error", "upstream_error", "error_description", ex.getResponseBodyAsString()));
		}
	}
}
