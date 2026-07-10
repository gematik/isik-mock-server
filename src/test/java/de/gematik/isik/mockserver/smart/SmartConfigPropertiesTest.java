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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link SmartConfigProperties}. */
@ActiveProfiles("smart-it")
class SmartConfigPropertiesTest {

  private static final String FHIR_BASE = "http://localhost:8080/fhir";
  private static final String KC_BASE = "http://localhost:9090/realms/fhir";

  @Test
  @DisplayName("authorizationEndpoint appends correct OIDC path")
  void authorizationEndpoint() {
    SmartConfigProperties props = props();
    assertThat(props.authorizationEndpoint()).isEqualTo(KC_BASE + "/protocol/openid-connect/auth");
  }

  @Test
  @DisplayName("tokenEndpoint appends correct OIDC path")
  void tokenEndpoint() {
    SmartConfigProperties props = props();
    assertThat(props.tokenEndpoint()).isEqualTo(KC_BASE + "/protocol/openid-connect/token");
  }

  @Test
  @DisplayName("jwksUri appends correct OIDC certs path")
  void jwksUri() {
    SmartConfigProperties props = props();
    assertThat(props.jwksUri()).isEqualTo(KC_BASE + "/protocol/openid-connect/certs");
  }

  @Test
  @DisplayName("introspectionEndpoint appends correct OIDC introspect path")
  void introspectionEndpoint() {
    SmartConfigProperties props = props();
    assertThat(props.introspectionEndpoint())
        .isEqualTo(KC_BASE + "/protocol/openid-connect/token/introspect");
  }

  @Test
  @DisplayName("revocationEndpoint appends correct OIDC revoke path")
  void revocationEndpoint() {
    SmartConfigProperties props = props();
    assertThat(props.revocationEndpoint()).isEqualTo(KC_BASE + "/protocol/openid-connect/revoke");
  }

  @Test
  @DisplayName("getFhirBaseUrl returns value set by setter")
  void getFhirBaseUrl() {
    SmartConfigProperties props = props();
    assertThat(props.getFhirBaseUrl()).isEqualTo(FHIR_BASE);
  }

  @Test
  @DisplayName("getKeycloakBaseUrl returns value set by setter")
  void getKeycloakBaseUrl() {
    SmartConfigProperties props = props();
    assertThat(props.getAuthorizationServerUrl()).isEqualTo(KC_BASE);
  }

  @Test
  @DisplayName("default values are used when no setters called")
  void defaults() {
    SmartConfigProperties props = new SmartConfigProperties();
    assertThat(props.getFhirBaseUrl()).isEqualTo("http://fhir.example.org/fhir");
    assertThat(props.getAuthorizationServerUrl())
        .isEqualTo("http://idp.example.org/realms/myrealm");
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  private SmartConfigProperties props() {
    SmartConfigProperties p = new SmartConfigProperties();
    p.setFhirBaseUrl(FHIR_BASE);
    p.setAuthorizationServerUrl(KC_BASE);
    return p;
  }
}
