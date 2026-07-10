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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SmartLaunchController}.
 *
 * <p>Verifies that EHR launch initiation creates a handle, redirects to the Keycloak authorization
 * endpoint with the correct query parameters, and that the handle is stored in the context store.
 */
class SmartLaunchControllerTest {

    private SmartConfigProperties properties;
    private SmartLaunchContextStore launchContextStore;
    private SmartLaunchController controller;

    private static final String FHIR_BASE = "http://fhir.example.org/fhir";
    private static final String KC_BASE = "http://keycloak.example.org/realms/fhir";
    private static final String AUTH_ENDPOINT = KC_BASE + "/protocol/openid-connect/auth";

    @BeforeEach
    void setUp() {
        properties = new SmartConfigProperties();
        properties.setFhirBaseUrl(FHIR_BASE);
        properties.setAuthorizationServerUrl(KC_BASE);
        launchContextStore = new SmartLaunchContextStore();
        controller = new SmartLaunchController(properties, launchContextStore);
    }

    @Nested
    @DisplayName("EHR launch redirect")
    class EhrLaunchRedirect {

        @Test
        @DisplayName("Returns HTTP 302 redirect")
        void returnsRedirect() {
            ResponseEntity<Void> response = controller.initiateLaunch("patient-1", "encounter-1");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        }

        @Test
        @DisplayName("Location header points to authorization endpoint")
        void locationHeaderPointsToAuthEndpoint() {
            ResponseEntity<Void> response = controller.initiateLaunch("patient-1", "encounter-1");
            assertThat(response.getHeaders().getLocation()).isNotNull();
            assertThat(response.getHeaders().getLocation().toString())
                    .startsWith(AUTH_ENDPOINT);
        }

        @Test
        @DisplayName("Location header contains iss parameter with FHIR base URL")
        void locationContainsIssParameter() {
            ResponseEntity<Void> response = controller.initiateLaunch("patient-1", "encounter-1");
            String location = response.getHeaders().getLocation().toString();
            assertThat(location).contains("iss=");
            assertThat(location).contains("fhir.example.org");
        }

        @Test
        @DisplayName("Location header contains launch parameter")
        void locationContainsLaunchParameter() {
            ResponseEntity<Void> response = controller.initiateLaunch("patient-1", null);
            String location = response.getHeaders().getLocation().toString();
            assertThat(location).contains("launch=");
        }

        @Test
        @DisplayName("Location header contains scope=launch")
        void locationContainsScopeParameter() {
            ResponseEntity<Void> response = controller.initiateLaunch(null, null);
            String location = response.getHeaders().getLocation().toString();
            assertThat(location).contains("scope=");
            assertThat(location).contains("launch");
        }

        @Test
        @DisplayName("Context is stored in launch context store")
        void contextStoredInStore() {
            assertThat(launchContextStore.size()).isZero();
            controller.initiateLaunch("patient-42", "encounter-99");
            assertThat(launchContextStore.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Works with null patient and encounter")
        void worksWithNullContext() {
            ResponseEntity<Void> response = controller.initiateLaunch(null, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(response.getHeaders().getLocation()).isNotNull();
        }
    }
}

