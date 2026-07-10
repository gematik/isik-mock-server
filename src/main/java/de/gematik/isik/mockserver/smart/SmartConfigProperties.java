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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration for the SMART on FHIR bridge.
 *
 * <p>Properties are read from {@code spring.security.oauth2.smart.*} in {@code application.yaml}.
 * Defaults match a local Keycloak + HAPI server configuration.
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "spring.security.oauth2.smart")
public class SmartConfigProperties {

	/**
	 * The public FHIR base URL served by this mock server. Used as the {@code iss} parameter in
	 * EHR-launch redirects and referenced in the SMART well-known document.
	 */
	private String fhirBaseUrl = "http://fhir.example.org/fhir";

	/** The base URL of the Authorization Server . */
	private String authorizationServerUrl = "http://idp.example.org/realms/myrealm";

	/** Returns the OIDC authorization endpoint. */
	public String authorizationEndpoint() {
		return authorizationServerUrl + "/protocol/openid-connect/auth";
	}

	/** Returns the token endpoint. */
	public String tokenEndpoint() {
		return authorizationServerUrl + "/protocol/openid-connect/token";
	}

	/** Returns the JWKS (public key set) URI */
	public String jwksUri() {
		return authorizationServerUrl + "/protocol/openid-connect/certs";
	}

	/** Returns the token introspection endpoint. */
	public String introspectionEndpoint() {
		return authorizationServerUrl + "/protocol/openid-connect/token/introspect";
	}

	/** Returns the token revocation endpoint. */
	public String revocationEndpoint() {
		return authorizationServerUrl + "/protocol/openid-connect/revoke";
	}
}
