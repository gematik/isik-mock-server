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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SmartConfigController}.
 *
 * <p>Verifies that the SMART well-known discovery document contains all required and recommended
 * fields as defined by the SMART App Launch v2.2.0 specification, with correct Java types.
 */
class SmartConfigControllerTest {

    private SmartConfigController controller;
    private SmartConfigProperties properties;

    private static final String FHIR_BASE = "http://fhir.example.com/fhir";
    private static final String KC_BASE = "http://keycloak.example.com/realms/fhir";

    @BeforeEach
    void setUp() {
        properties = new SmartConfigProperties();
        properties.setFhirBaseUrl(FHIR_BASE);
        properties.setAuthorizationServerUrl(KC_BASE);
        controller = new SmartConfigController(properties);
    }

    @Nested
    @DisplayName("Required fields (SMART App Launch 2.2.0)")
    class RequiredFields {

        @Test
        @DisplayName("issuer → Keycloak realm URL")
        void issuer() {
            assertThat(doc()).containsKey("issuer");
            assertThat(doc().get("issuer")).isEqualTo(KC_BASE);
        }

        @Test
        @DisplayName("jwks_uri → derived from keycloakBaseUrl")
        void jwksUri() {
            assertThat(doc().get("jwks_uri"))
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/certs");
        }

        @Test
        @DisplayName("authorization_endpoint → derived from keycloakBaseUrl")
        void authorizationEndpoint() {
            assertThat(doc().get("authorization_endpoint"))
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/auth");
        }

        @Test
        @DisplayName("token_endpoint → derived from keycloakBaseUrl")
        void tokenEndpoint() {
            assertThat(doc().get("token_endpoint"))
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/token");
        }

        @Test
        @DisplayName("token_endpoint_auth_methods_supported is a List containing private_key_jwt")
        void tokenEndpointAuthMethodsSupported() {
            Object value = doc().get("token_endpoint_auth_methods_supported");
            assertThat(value).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> methods = (List<String>) value;
            assertThat(methods)
                    .contains("client_secret_basic", "client_secret_post", "private_key_jwt");
        }

        @Test
        @DisplayName("token_endpoint_auth_signing_alg_values_supported contains RS384 and ES384")
        void tokenEndpointAuthSigningAlgValuesSupported() {
            Object value = doc().get("token_endpoint_auth_signing_alg_values_supported");
            assertThat(value).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> algs = (List<String>) value;
            assertThat(algs).contains("RS384", "ES384");
        }

        @Test
        @DisplayName("grant_types_supported is a List containing authorization_code and client_credentials")
        void grantTypesSupported() {
            Object value = doc().get("grant_types_supported");
            assertThat(value).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> types = (List<String>) value;
            assertThat(types).contains("authorization_code", "client_credentials", "refresh_token");
        }

        @Test
        @DisplayName("response_types_supported is a List<String> not a bare String")
        void responseTypesSupportedIsAList() {
            Object value = doc().get("response_types_supported");
            assertThat(value)
                    .as("response_types_supported must be a List, not a bare String")
                    .isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> types = (List<String>) value;
            assertThat(types).containsExactly("code");
        }

        @Test
        @DisplayName("code_challenge_methods_supported contains S256")
        void codeChallengeMethodsSupported() {
            Object value = doc().get("code_challenge_methods_supported");
            assertThat(value).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> methods = (List<String>) value;
            assertThat(methods).contains("S256");
        }

        @Test
        @DisplayName("scopes_supported contains SMART and OIDC scopes")
        void scopesSupported() {
            Object value = doc().get("scopes_supported");
            assertThat(value).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> scopes = (List<String>) value;
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
    @DisplayName("Recommended fields")
    class RecommendedFields {

        @Test
        @DisplayName("introspection_endpoint is present")
        void introspectionEndpoint() {
            assertThat(doc().get("introspection_endpoint"))
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/token/introspect");
        }

        @Test
        @DisplayName("revocation_endpoint is present")
        void revocationEndpoint() {
            assertThat(doc().get("revocation_endpoint"))
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/revoke");
        }
    }

    @Nested
    @DisplayName("SMART capabilities")
    class SmartCapabilities {

        @Test
        @DisplayName("capabilities is a List")
        void capabilitiesIsAList() {
            Object value = doc().get("capabilities");
            assertThat(value).isInstanceOf(List.class);
        }

        @Test
        @DisplayName("capabilities contains all required capability tokens")
        void capabilitiesContainsRequiredTokens() {
            @SuppressWarnings("unchecked")
            List<String> caps = (List<String>) doc().get("capabilities");
            assertThat(caps)
                    .contains(
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
                            "permission-offline");
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Map<String, Object> doc() {
        return controller.config();
    }
}

