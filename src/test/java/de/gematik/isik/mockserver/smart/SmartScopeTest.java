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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SmartScopeTest {

  // ---------------------------------------------------------------------------
  // Happy-path parsing
  // ---------------------------------------------------------------------------

  @Test
  void parse_patientWildcardReadSearch_isRecognised() {
    Optional<SmartScope> result = SmartScope.parse("patient/*.rs");

    assertThat(result).isPresent();
    SmartScope scope = result.get();
    assertThat(scope.accessType()).isEqualTo(SmartAuthorizationAccessType.PATIENT);
    assertThat(scope.isWildcard()).isTrue();
    assertThat(scope.canRead()).isTrue();
    assertThat(scope.canSearch()).isTrue();
    assertThat(scope.canCreate()).isFalse();
    assertThat(scope.canUpdate()).isFalse();
    assertThat(scope.canDelete()).isFalse();
  }

  @Test
  void parse_patientSpecificResourceCruds_allPermissionsGranted() {
    Optional<SmartScope> result = SmartScope.parse("patient/AllergyIntolerance.cruds");

    assertThat(result).isPresent();
    SmartScope scope = result.get();
    assertThat(scope.accessType()).isEqualTo(SmartAuthorizationAccessType.PATIENT);
    assertThat(scope.resourceType()).isEqualTo("AllergyIntolerance");
    assertThat(scope.isWildcard()).isFalse();
    assertThat(scope.canCreate()).isTrue();
    assertThat(scope.canRead()).isTrue();
    assertThat(scope.canUpdate()).isTrue();
    assertThat(scope.canDelete()).isTrue();
    assertThat(scope.canSearch()).isTrue();
  }

  @Test
  void parse_patientAllergyReadOnly_onlyReadGranted() {
    Optional<SmartScope> result = SmartScope.parse("patient/AllergyIntolerance.rs");

    assertThat(result).isPresent();
    SmartScope scope = result.get();
    assertThat(scope.canRead()).isTrue();
    assertThat(scope.canSearch()).isTrue();
    assertThat(scope.canCreate()).isFalse();
    assertThat(scope.canUpdate()).isFalse();
    assertThat(scope.canDelete()).isFalse();
  }

  @Test
  void parse_patientAllergyWriteOnly_onlyWriteGranted() {
    Optional<SmartScope> result = SmartScope.parse("patient/AllergyIntolerance.cud");

    assertThat(result).isPresent();
    SmartScope scope = result.get();
    assertThat(scope.canCreate()).isTrue();
    assertThat(scope.canUpdate()).isTrue();
    assertThat(scope.canDelete()).isTrue();
    assertThat(scope.canRead()).isFalse();
    assertThat(scope.canSearch()).isFalse();
  }

  @Test
  void parse_userWildcardCruds_userContextAllResources() {
    Optional<SmartScope> result = SmartScope.parse("user/*.cruds");

    assertThat(result).isPresent();
    SmartScope scope = result.get();
    assertThat(scope.accessType()).isEqualTo(SmartAuthorizationAccessType.USER);
    assertThat(scope.isWildcard()).isTrue();
    assertThat(scope.canCreate()).isTrue();
    assertThat(scope.canRead()).isTrue();
    assertThat(scope.canUpdate()).isTrue();
    assertThat(scope.canDelete()).isTrue();
    assertThat(scope.canSearch()).isTrue();
  }

  @Test
  void parse_systemPatientRead_systemContext() {
    Optional<SmartScope> result = SmartScope.parse("system/Patient.r");

    assertThat(result).isPresent();
    SmartScope scope = result.get();
    assertThat(scope.accessType()).isEqualTo(SmartAuthorizationAccessType.SYSTEM);
    assertThat(scope.resourceType()).isEqualTo("Patient");
    assertThat(scope.canRead()).isTrue();
    assertThat(scope.canCreate()).isFalse();
  }

  @Test
  void parse_scopeWithQueryParam_isRecognised() {
    // SMART v2 allows filter params after '?'
    Optional<SmartScope> result = SmartScope.parse("patient/Observation.rs?category=vital-signs");

    assertThat(result).isPresent();
    SmartScope scope = result.get();
    assertThat(scope.resourceType()).isEqualTo("Observation");
    assertThat(scope.canRead()).isTrue();
    assertThat(scope.canSearch()).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Empty / invalid scope strings
  // ---------------------------------------------------------------------------

  @Test
  void parse_emptyString_returnsEmpty() {
    assertThat(SmartScope.parse("")).isEmpty();
  }

  @Test
  void parse_nullString_returnsEmpty() {
    assertThat(SmartScope.parse(null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "openid",
        "profile",
        "fhirUser",
        "offline_access",
        "launch",
        "launch/patient",
        "unknown/Observation.r",
        "patient/Observation",
        "patient/*.xyz",
        "patient/Observation.",
      })
  void parse_nonSmartOrMalformedScopes_returnEmpty(final String scope) {
    assertThat(SmartScope.parse(scope)).isEmpty();
  }
}

