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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SmartTokenIntrospectionController}.
 *
 * <p>Verifies that introspection requests are proxied to the upstream Keycloak introspection
 * endpoint, that Authorization headers are forwarded, and that upstream error responses are
 * mirrored to the caller.
 */
@ExtendWith(MockitoExtension.class)
class SmartTokenIntrospectionControllerTest {

    private static final String KC_BASE = "http://keycloak.example.org/realms/fhir";
    private static final String INTROSPECT_URL =
            KC_BASE + "/protocol/openid-connect/token/introspect";

    @Mock private RestTemplate restTemplate;
    private SmartConfigProperties properties;
    private SmartTokenIntrospectionController controller;

    @BeforeEach
    void setUp() {
        properties = new SmartConfigProperties();
        properties.setFhirBaseUrl("http://fhir.example.org/fhir");
        properties.setAuthorizationServerUrl(KC_BASE);
        controller = new SmartTokenIntrospectionController(properties, restTemplate);
    }

    @Nested
    @DisplayName("Successful introspection")
    class SuccessfulIntrospection {

        @Test
        @DisplayName("Proxies POST to Keycloak introspection endpoint")
        void proxiesToKeycloak() {
            Map<String, Object> keycloakResponse = Map.of("active", true, "sub", "user-1");
            when(restTemplate.postForEntity(eq(INTROSPECT_URL), any(), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(keycloakResponse));

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("token", "some-access-token");
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setBasicAuth("client-id", "client-secret");

            ResponseEntity<Map<String, Object>> response =
                    controller.introspect(formData, requestHeaders);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("active", true);
            verify(restTemplate).postForEntity(eq(INTROSPECT_URL), any(HttpEntity.class), eq(Map.class));
        }

        @Test
        @DisplayName("Returns active=false for inactive token")
        void returnsInactiveForExpiredToken() {
            Map<String, Object> keycloakResponse = Map.of("active", false);
            when(restTemplate.postForEntity(eq(INTROSPECT_URL), any(), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(keycloakResponse));

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("token", "expired-token");
            HttpHeaders headers = new HttpHeaders();

            ResponseEntity<Map<String, Object>> response = controller.introspect(formData, headers);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("active", false);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Returns upstream status on HTTP 401 from Keycloak")
        void mirrorsUpstream401() {
            when(restTemplate.postForEntity(eq(INTROSPECT_URL), any(), eq(Map.class)))
                    .thenThrow(
                            HttpClientErrorException.create(
                                    HttpStatus.UNAUTHORIZED,
                                    "Unauthorized",
                                    HttpHeaders.EMPTY,
                                    new byte[0],
                                    null));

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("token", "bad-token");
            HttpHeaders headers = new HttpHeaders();

            ResponseEntity<Map<String, Object>> response = controller.introspect(formData, headers);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Header forwarding")
    class HeaderForwarding {

        @Test
        @DisplayName("Authorization header is forwarded to Keycloak")
        void authorizationHeaderForwarded() {
            Map<String, Object> keycloakResponse = Map.of("active", true);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(keycloakResponse));

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("token", "tok");
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setBasicAuth("myClientId", "myClientSecret");

            controller.introspect(formData, requestHeaders);

            // Verify the RestTemplate was called with an entity containing Authorization header
            verify(restTemplate)
                    .postForEntity(
                            eq(INTROSPECT_URL),
                            any(HttpEntity.class),
                            eq(Map.class));
        }

        @Test
        @DisplayName("No Authorization header — still proxies without it")
        void noAuthHeaderStillProxies() {
            Map<String, Object> keycloakResponse = Map.of("active", false);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(keycloakResponse));

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("token", "tok");
            HttpHeaders emptyHeaders = new HttpHeaders();

            ResponseEntity<Map<String, Object>> response = controller.introspect(formData, emptyHeaders);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}


