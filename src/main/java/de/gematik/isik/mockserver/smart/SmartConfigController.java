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

import ca.uhn.fhir.jpa.starter.security.Oauth2SecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the SMART on FHIR discovery document at {@code /fhir/.well-known/smart-configuration}.
 *
 * <p>The response complies with the SMART App Launch v2.2.0 specification (required + recommended
 * fields). It is only active when {@code spring.security.oauth2.enable=true}.
 *
 * <p>The endpoint is explicitly listed as {@code permitAll} in {@link Oauth2SecurityConfig} so that
 * unauthenticated clients can discover the authorization server. Spring's {@link
 * org.springframework.web.cors.CorsConfigurationSource} also applies a permissive CORS policy to
 * this path.
 */
@ConditionalOnProperty(
		name = {"spring.security.oauth2.enable"},
		havingValue = "true")
@RestController
public class SmartConfigController {

	private final SmartConfigProperties smartConfigProperties;

	public SmartConfigController(final SmartConfigProperties smartConfigProperties) {
		this.smartConfigProperties = smartConfigProperties;
	}

	/**
	 * Returns the SMART on FHIR well-known configuration document.
	 *
	 * <p>Mapped to {@code /fhir/.well-known/smart-configuration} so that the path falls under the
	 * FHIR base context and is reachable relative to the {@code iss} parameter the server advertises.
	 */
	@GetMapping("/fhir/.well-known/smart-configuration")
	public Map<String, Object> config() {
		Map<String, Object> doc = new LinkedHashMap<>();

		// --- REQUIRED fields (SMART App Launch 2.2.0) ---
		doc.put("issuer", smartConfigProperties.getAuthorizationServerUrl());
		doc.put("jwks_uri", smartConfigProperties.jwksUri());
		doc.put("authorization_endpoint", smartConfigProperties.authorizationEndpoint());
		doc.put("token_endpoint", smartConfigProperties.tokenEndpoint());
		doc.put(
				"token_endpoint_auth_methods_supported",
				List.of("client_secret_basic", "client_secret_post", "private_key_jwt"));
		doc.put("token_endpoint_auth_signing_alg_values_supported", List.of("RS384", "ES384"));
		doc.put("grant_types_supported", List.of("authorization_code", "client_credentials", "refresh_token"));
		doc.put("response_types_supported", List.of("code"));
		doc.put("code_challenge_methods_supported", List.of("S256"));
		doc.put(
				"scopes_supported",
				List.of(
						"openid",
						"fhirUser",
						"launch",
						"launch/patient",
						"launch/encounter",
						"patient/*.cruds",
						"user/*.cruds",
						"system/*.cruds",
						"offline_access"));

		// --- RECOMMENDED fields ---
		doc.put("introspection_endpoint", smartConfigProperties.introspectionEndpoint());
		doc.put("revocation_endpoint", smartConfigProperties.revocationEndpoint());

		// --- SMART capabilities ---
		doc.put(
				"capabilities",
				List.of(
						"launch-standalone",
						"launch-ehr",
						"client-public",
						"client-confidential-symmetric",
						"client-confidential-asymmetric",
						"sso-openid-connect",
						"context-standalone-patient",
						"context-ehr-patient",
						"context-ehr-encounter",
						"permission-patient",
						"permission-user",
						"permission-v2",
						"permission-offline"));

		return doc;
	}
}
