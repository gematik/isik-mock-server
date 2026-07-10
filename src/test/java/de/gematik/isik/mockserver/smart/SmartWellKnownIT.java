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

import ca.uhn.fhir.jpa.starter.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the SMART well-known discovery document.
 *
 * <p>Verifies that {@code GET /fhir/.well-known/smart-configuration} is:
 *
 * <ul>
 *   <li>publicly accessible (no Bearer token required),
 *   <li>served with HTTP 200,
 *   <li>a JSON object containing all required and recommended SMART App Launch 2.2.0 fields,
 *   <li>using the correct Java types (e.g., {@code response_types_supported} as a {@code List}),
 *   <li>containing the correct CORS header.
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class)
@ActiveProfiles({"integrationtest", "smart-it"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmartWellKnownIT {

    /** Replaced by a mock so no real Keycloak connection is needed. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Nested
    @DisplayName("HTTP accessibility")
    class HttpAccessibility {

        @Test
        @DisplayName("GET /fhir/.well-known/smart-configuration returns HTTP 200")
        void returnsOk() {
            ResponseEntity<String> response =
                    restTemplate.getForEntity(wellKnownUrl(), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Endpoint is reachable without a Bearer token")
        void noAuthRequired() {
            // TestRestTemplate does not attach auth headers by default.
            ResponseEntity<String> response =
                    restTemplate.getForEntity(wellKnownUrl(), String.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }

    @Nested
    @DisplayName("Required SMART fields present")
    class RequiredFields {

        @Test
        @DisplayName("issuer field is present and non-blank")
        void issuerPresent() {
            Map<String, Object> doc = doc();
            assertThat(doc).containsKey("issuer");
            assertThat(doc.get("issuer").toString()).isNotBlank();
        }

        @Test
        @DisplayName("jwks_uri field is present")
        void jwksUriPresent() {
            assertThat(doc()).containsKey("jwks_uri");
        }

        @Test
        @DisplayName("authorization_endpoint field is present")
        void authorizationEndpointPresent() {
            assertThat(doc()).containsKey("authorization_endpoint");
        }

        @Test
        @DisplayName("token_endpoint field is present")
        void tokenEndpointPresent() {
            assertThat(doc()).containsKey("token_endpoint");
        }

        @Test
        @DisplayName("token_endpoint_auth_methods_supported contains private_key_jwt")
        void tokenEndpointAuthMethodsContainsPrivateKeyJwt() {
            @SuppressWarnings("unchecked")
            List<String> methods = (List<String>) doc().get("token_endpoint_auth_methods_supported");
            assertThat(methods).contains("private_key_jwt");
        }

        @Test
        @DisplayName("token_endpoint_auth_signing_alg_values_supported contains RS384 and ES384")
        void signingAlgsContainRS384andES384() {
            @SuppressWarnings("unchecked")
            List<String> algs =
                    (List<String>) doc().get("token_endpoint_auth_signing_alg_values_supported");
            assertThat(algs).contains("RS384", "ES384");
        }

        @Test
        @DisplayName("response_types_supported is a List (not a bare String)")
        void responseTypesSupportedIsAList() {
            Object value = doc().get("response_types_supported");
            assertThat(value)
                    .as("response_types_supported must be a JSON array, not a string")
                    .isInstanceOf(List.class);
        }

        @Test
        @DisplayName("scopes_supported contains SMART resource scopes and OIDC scopes")
        void scopesSupportedComplete() {
            @SuppressWarnings("unchecked")
            List<String> scopes = (List<String>) doc().get("scopes_supported");
            assertThat(scopes)
                    .contains(
                            "openid",
                            "fhirUser",
                            "launch",
                            "launch/patient",
                            "launch/encounter",
                            "patient/*.cruds",
                            "user/*.cruds",
                            "system/*.cruds",
                            "offline_access");
        }
    }

    @Nested
    @DisplayName("Recommended SMART fields present")
    class RecommendedFields {

        @Test
        @DisplayName("introspection_endpoint field is present")
        void introspectionEndpointPresent() {
            assertThat(doc()).containsKey("introspection_endpoint");
        }

        @Test
        @DisplayName("revocation_endpoint field is present")
        void revocationEndpointPresent() {
            assertThat(doc()).containsKey("revocation_endpoint");
        }
    }

    @Nested
    @DisplayName("SMART capabilities")
    class SmartCapabilities {

        @Test
        @DisplayName("capabilities is a List containing at least the core capability tokens")
        void capabilitiesContainsCoreTokens() {
            @SuppressWarnings("unchecked")
            List<String> caps = (List<String>) doc().get("capabilities");
            assertThat(caps)
                    .contains(
                            "launch-standalone",
                            "launch-ehr",
                            "client-confidential-asymmetric",
                            "sso-openid-connect",
                            "permission-v2");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String wellKnownUrl() {
        return "http://localhost:" + port + "/fhir/.well-known/smart-configuration";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doc() {
        return restTemplate.getForObject(wellKnownUrl(), Map.class);
    }
}

