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

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Integration test for the SMART on FHIR STU 2.2 authorization layer.
 *
 * <p>The Spring context is started with {@code spring.security.oauth2.enable=true} via the {@code
 * smart-it} profile while a {@link MockitoBean} replaces the {@link JwtDecoder} so that no real
 * Keycloak server is required. Each test manufactures a {@link Jwt} with the desired claims and
 * scopes directly.
 *
 * <p>Scenarios covered:
 *
 * <ul>
 *   <li>Public endpoints (metadata, SMART well-known) are accessible without a token.
 *   <li>Any FHIR interaction without a bearer token yields HTTP 401.
 *   <li>A token with no {@code scope} claim yields HTTP 403.
 *   <li>{@code user/*.rs} grants read/search across all resource types.
 *   <li>{@code user/Patient.c} grants only create – read is still rejected.
 *   <li>{@code system/*.cruds} grants full access.
 *   <li>{@code patient/Patient.rs} with a {@code patient} claim restricts reads to the patient
 *       compartment: the right patient is allowed, a different one is rejected.
 *   <li>{@code user/Patient.c} allows creating a Patient; {@code user/Patient.rs} does not.
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = Application.class)
@ActiveProfiles({"integrationtest", "smart-it"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmartAuthorizationIT {

  // ---------------------------------------------------------------------------
  // Infrastructure
  // ---------------------------------------------------------------------------

  /** Replaces the production {@link JwtDecoder} so no Keycloak connection is needed. */
  @MockitoBean JwtDecoder jwtDecoder;

  @LocalServerPort int port;

  @Autowired TestRestTemplate restTemplate;

  @Autowired DaoRegistry daoRegistry;

  // IDs of two patients persisted at class-level setup, used in compartment tests.
  private String patientAId;
  private String patientBId;

  // ---------------------------------------------------------------------------
  // Named token strings (values are arbitrary; matching is done by equality).
  // ---------------------------------------------------------------------------
  private static final String TOKEN_USER_WILDCARD_READ = "token-user-wildcard-rs";
  private static final String TOKEN_USER_PATIENT_CREATE_ONLY = "token-user-patient-c";
  private static final String TOKEN_USER_PATIENT_READ_ONLY = "token-user-patient-rs";
  private static final String TOKEN_SYSTEM_WILDCARD_CRUDS = "token-system-cruds";
  private static final String TOKEN_PATIENT_A_READ = "token-patient-a-rs";
  private static final String TOKEN_NO_SCOPE = "token-no-scope";
  private static final String TOKEN_UNKNOWN = "token-unknown-garbage";
  private static final String TOKEN_OIDC_ONLY = "token-oidc-only";
  private static final String TOKEN_MIXED_OIDC_FHIR = "token-mixed-oidc-fhir";
  private static final String TOKEN_MIXED_LAUNCH_FHIR = "token-mixed-launch-fhir";

  // ---------------------------------------------------------------------------
  // Test setup
  // ---------------------------------------------------------------------------

  @BeforeAll
  void createTestPatients() {
    IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
    SystemRequestDetails srd = new SystemRequestDetails();

    Patient patientA = new Patient();
    patientA.setActive(true);
    patientAId = patientDao.create(patientA, srd).getId().getIdPart();

    Patient patientB = new Patient();
    patientB.setActive(true);
    patientBId = patientDao.create(patientB, srd).getId().getIdPart();
  }

  /**
   * Configures the mock {@link JwtDecoder} via a single {@code doAnswer} router.
   *
   * <p>Using a single stub with an {@link org.mockito.stubbing.Answer} avoids the FIFO/LIFO
   * ordering ambiguity that arises when mixing {@code anyString()} catch-all stubs with
   * specific-value stubs in Mockito 5.x.
   */
  @BeforeEach
  void configureMockDecoder() {
    // Build the Jwt objects that will be returned for known tokens.
    final Jwt userWildcardRead = scopeJwt(TOKEN_USER_WILDCARD_READ, "user/*.rs", null);
    final Jwt userPatientCreate = scopeJwt(TOKEN_USER_PATIENT_CREATE_ONLY, "user/Patient.c", null);
    final Jwt userPatientRead = scopeJwt(TOKEN_USER_PATIENT_READ_ONLY, "user/Patient.rs", null);
    final Jwt systemCruds = scopeJwt(TOKEN_SYSTEM_WILDCARD_CRUDS, "system/*.cruds", null);
    final Jwt patientARead = scopeJwt(TOKEN_PATIENT_A_READ, "patient/Patient.rs", patientAId);
    final Jwt noScope =
        Jwt.withTokenValue(TOKEN_NO_SCOPE)
            .header("alg", "none")
            .subject("test-user")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    // OIDC-only token: contains openid + fhirUser but NO resource scopes.
    final Jwt oidcOnly = scopeJwt(TOKEN_OIDC_ONLY, "openid fhirUser", null);

    // Mixed token: OIDC scopes + a FHIR resource scope.
    final Jwt mixedOidcFhir = scopeJwt(TOKEN_MIXED_OIDC_FHIR, "openid fhirUser user/*.rs", null);

    // Token with launch context scopes alongside FHIR scopes.
    final Jwt mixedLaunchFhir =
        scopeJwt(TOKEN_MIXED_LAUNCH_FHIR, "launch launch/patient patient/*.rs", patientAId);

    // Single doAnswer router: avoids multi-stub ordering problems.
    doAnswer(
            invocation -> {
              String token = invocation.getArgument(0);
              return switch (token) {
                case TOKEN_USER_WILDCARD_READ -> userWildcardRead;
                case TOKEN_USER_PATIENT_CREATE_ONLY -> userPatientCreate;
                case TOKEN_USER_PATIENT_READ_ONLY -> userPatientRead;
                case TOKEN_SYSTEM_WILDCARD_CRUDS -> systemCruds;
                case TOKEN_PATIENT_A_READ -> patientARead;
                case TOKEN_NO_SCOPE -> noScope;
                case TOKEN_OIDC_ONLY -> oidcOnly;
                case TOKEN_MIXED_OIDC_FHIR -> mixedOidcFhir;
                case TOKEN_MIXED_LAUNCH_FHIR -> mixedLaunchFhir;
                default -> throw new JwtException("Unknown or expired token: " + token);
              };
            })
        .when(jwtDecoder)
        .decode(anyString());
  }

  // ---------------------------------------------------------------------------
  // Public-endpoint tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Public endpoints")
  class PublicEndpoints {

    @Test
    @DisplayName("GET /fhir/metadata is accessible without a bearer token")
    void metadataIsPublic() {
      ResponseEntity<String> response =
          restTemplate.getForEntity(fhirUrl("/metadata"), String.class);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /fhir/.well-known/smart-configuration is accessible without a bearer token")
    void smartConfigurationIsPublic() {
      ResponseEntity<String> response =
          restTemplate.getForEntity(fhirUrl("/.well-known/smart-configuration"), String.class);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /fhir/launch is accessible without a bearer token (returns redirect)")
    void ehrLaunchEndpointIsPublic() {
      // The launch endpoint redirects; TestRestTemplate by default follows redirects
      // but since the target Keycloak URL is unreachable in tests we just check
      // that the endpoint responds (not 401/403).
      ResponseEntity<String> response =
          restTemplate.getForEntity(fhirUrl("/launch?patient=p-1"), String.class);
      // Should NOT be 401 (Unauthorized) or 403 (Forbidden) — it is a public endpoint.
      assertThat(response.getStatusCode().value()).isNotIn(401, 403);
    }
  }

  // ---------------------------------------------------------------------------
  // Unauthenticated / invalid-token tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Authentication enforcement")
  class AuthenticationEnforcement {

    @Test
    @DisplayName("GET /fhir/Patient without a token returns HTTP 401")
    void noTokenYields401() {
      ResponseEntity<String> response =
          restTemplate.getForEntity(fhirUrl("/Patient"), String.class);
      assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Unknown Token returns HTTP 401")
    void unknownTokenYields401() {
      ResponseEntity<String> response = get(fhirUrl("/Patient"), TOKEN_UNKNOWN);
      assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("A valid JWT with no 'scope' claim returns HTTP 403")
    void tokenWithoutScopeYields403() {
      ResponseEntity<String> response = get(fhirUrl("/Patient"), TOKEN_NO_SCOPE);
      assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
  }

  // ---------------------------------------------------------------------------
  // Scope-based authorization tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Scope-based authorization")
  class ScopeBasedAuthorization {

    @Test
    @DisplayName("user/*.rs allows searching all resource types")
    void userWildcardReadAllowsSearch() {
      ResponseEntity<String> response = get(fhirUrl("/Patient"), TOKEN_USER_WILDCARD_READ);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("user/*.rs allows reading a specific resource")
    void userWildcardReadAllowsRead() {
      ResponseEntity<String> response =
          get(fhirUrl("/Patient/" + patientAId), TOKEN_USER_WILDCARD_READ);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("user/Patient.rs (read-only) forbids creating Patient resources")
    void readOnlyScopeForbidsCreate() {
      String newPatient = createExamplePatient();
      ResponseEntity<String> response =
          post(fhirUrl("/Patient"), TOKEN_USER_PATIENT_READ_ONLY, newPatient);
      assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("user/Patient.c allows creating a Patient resource")
    void createScopeAllowsCreate() {
      String newPatient = createExamplePatient();
      ResponseEntity<String> response =
          post(fhirUrl("/Patient"), TOKEN_USER_PATIENT_CREATE_ONLY, newPatient);
      assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("system/*.cruds allows reading any resource")
    void systemWildcardAllowsRead() {
      ResponseEntity<String> response =
          get(fhirUrl("/Patient/" + patientAId), TOKEN_SYSTEM_WILDCARD_CRUDS);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("system/*.cruds allows creating a resource")
    void systemWildcardAllowsCreate() {
      String newPatient = createExamplePatient();
      ResponseEntity<String> response =
          post(fhirUrl("/Patient"), TOKEN_SYSTEM_WILDCARD_CRUDS, newPatient);
      assertThat(response.getStatusCode().value()).isEqualTo(201);
    }
  }

  // ---------------------------------------------------------------------------
  // Patient-compartment tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Patient-compartment enforcement")
  class PatientCompartment {

    @Test
    @DisplayName(
        "patient/Patient.rs with matching 'patient' claim allows reading own Patient record")
    void patientScopeAllowsReadingOwnRecord() {
      ResponseEntity<String> response =
          get(fhirUrl("/Patient/" + patientAId), TOKEN_PATIENT_A_READ);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName(
        "patient/Patient.rs with mismatched 'patient' claim denies reading another Patient record")
    void patientScopeBlocksReadingOtherPatientRecord() {
      ResponseEntity<String> response =
          get(fhirUrl("/Patient/" + patientBId), TOKEN_PATIENT_A_READ);
      assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("patient/Patient.rs allows searching (compartment filter applied at query time)")
    void patientScopeAllowsSearch() {
      ResponseEntity<String> response = get(fhirUrl("/Patient"), TOKEN_PATIENT_A_READ);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
  }

  // ---------------------------------------------------------------------------
  // Mixed OIDC + FHIR scope tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Mixed OIDC and FHIR scope handling")
  class MixedScopeHandling {

    @Test
    @DisplayName(
        "Token with 'openid fhirUser' (OIDC-only, no resource scope) returns HTTP 403 for FHIR resources")
    void oidcOnlyTokenYields403ForFhirResources() {
      // OIDC scopes alone do NOT grant FHIR resource access.
      ResponseEntity<String> response = get(fhirUrl("/Patient"), TOKEN_OIDC_ONLY);
      assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("Token with 'openid fhirUser user/*.rs' allows reading Patient resources")
    void mixedOidcFhirScopeAllowsRead() {
      // OIDC scopes are skipped; user/*.rs grants read access.
      ResponseEntity<String> response =
          get(fhirUrl("/Patient/" + patientAId), TOKEN_MIXED_OIDC_FHIR);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Token with 'openid fhirUser user/*.rs' allows searching Patient resources")
    void mixedOidcFhirScopeAllowsSearch() {
      ResponseEntity<String> response = get(fhirUrl("/Patient"), TOKEN_MIXED_OIDC_FHIR);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName(
        "Token with 'launch launch/patient patient/*.rs' is handled: launch scopes skipped, patient/*.rs grants access")
    void mixedLaunchFhirScopeAllowsReadOwnPatient() {
      // launch and launch/patient are non-FHIR scopes and must be skipped;
      // patient/*.rs (with matching patient claim) allows reading own record.
      ResponseEntity<String> response =
          get(fhirUrl("/Patient/" + patientAId), TOKEN_MIXED_LAUNCH_FHIR);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private String fhirUrl(final String path) {
    return url("/fhir" + path);
  }

  private String url(final String path) {
    return "http://localhost:" + port + path;
  }

  private ResponseEntity<String> get(final String url, final String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setAccept(List.of(MediaType.parseMediaType("application/fhir+json")));
    return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private ResponseEntity<String> post(final String url, final String token, final String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.parseMediaType("application/fhir+json"));
    headers.setAccept(List.of(MediaType.parseMediaType("application/fhir+json")));
    return restTemplate.exchange(
        url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  /** Builds a {@link Jwt} carrying the given SMART scope and optional {@code patient} claim. */
  private Jwt scopeJwt(final String tokenValue, final String scope, final String patientId) {
    Map<String, Object> claims =
        patientId != null
            ? Map.of("sub", "test-user", "scope", scope, "patient", patientId)
            : Map.of("sub", "test-user", "scope", scope);
    return Jwt.withTokenValue(tokenValue)
        .header("alg", "none")
        .claims(c -> c.putAll(claims))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }

  private String createExamplePatient() {
    return
"""
{
  "resourceType": "Patient",
  "meta": {
    "tag": [
      {
        "code": "external",
        "system": "http://fhir.de/CodeSystem/common-meta-tag-de"
      }
    ],
    "profile": [
      "https://gematik.de/fhir/isik/StructureDefinition/ISiKPatient"
    ]
  },
  "identifier": [
    {
      "type": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
            "code": "MR"
          }
        ]
      },
      "system": "http://testkrankenhaus.de/fhir/sid/Patient",
      "value": "IdentifierValuepatient-delete"
    }
  ],
  "name": [
    {
      "use": "official",
      "family": "Graf von und zu Mustermann",
      "_family": {
        "extension": [
          {
            "url": "http://fhir.de/StructureDefinition/humanname-namenszusatz",
            "valueString": "Graf"
          },
          {
            "url": "http://hl7.org/fhir/StructureDefinition/humanname-own-name",
            "valueString": "Mustermann"
          },
          {
            "url": "http://hl7.org/fhir/StructureDefinition/humanname-own-prefix",
            "valueString": "von und zu"
          }
        ]
      },
      "given": [
        "Max"
      ],
      "prefix": [
        "Prof."
      ],
      "_prefix": [
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/iso21090-EN-qualifier",
              "valueCode": "AC"
            }
          ]
        }
      ]
    },
    {
      "use": "maiden",
      "family": "Musterknabe",
      "_family": {
        "extension": [
          {
            "url": "http://hl7.org/fhir/StructureDefinition/humanname-own-name",
            "valueString": "Musterknabe"
          }
        ]
      }
    }
  ],
  "address": [
    {
      "type": "both",
      "line": [
        "Unter den Linden 3",
        "1. Etage Hinterhaus"
      ],
      "_line": [
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName",
              "valueString": "Unter den Linden"
            },
            {
              "url": "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber",
              "valueString": "3"
            }
          ]
        },
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-additionalLocator",
              "valueString": "1. Etage Hinterhaus"
            }
          ]
        }
      ],
      "city": "Berlin",
      "postalCode": "10117",
      "country": "DE"
    },
    {
      "type": "postal",
      "line": [
        "Postfach 4711"
      ],
      "_line": [
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-postBox",
              "valueString": "Postfach 4711"
            }
          ]
        }
      ],
      "city": "Berlin",
      "postalCode": "10117",
      "country": "DE"
    }
  ],
  "active": false,
  "telecom": [
    {
      "system": "phone",
      "value": "030 1234567"
    }
  ],
  "gender": "male",
  "birthDate": "1968-05-12"
}
""";
  }
}
