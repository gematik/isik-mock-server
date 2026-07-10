package de.gematik.isik.mockserver.interceptor;

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

import de.gematik.isik.mockserver.smart.SmartConfigProperties;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Extension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link SmartCapabilityStatementInterceptor}.
 *
 * <p>Verifies the SMART on FHIR oauth-uris extension structure added to the CapabilityStatement by
 * the interceptor hook.
 */
class SmartCapabilityStatementInterceptorTest {

    private static final String OAUTH_URIS_URL =
            "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris";
    private static final String KC_BASE = "http://keycloak.example.org/realms/fhir";

    private SmartCapabilityStatementInterceptor interceptor;

    @BeforeEach
    void setUp() {
        SmartConfigProperties properties = new SmartConfigProperties();
        properties.setFhirBaseUrl("http://fhir.example.org/fhir");
        properties.setAuthorizationServerUrl(KC_BASE);

        interceptor = new SmartCapabilityStatementInterceptor(properties);
        // Enable SMART mode
        ReflectionTestUtils.setField(interceptor, "shouldSecureEndpoints", true);
    }

    @Nested
    @DisplayName("oauth-uris extension structure")
    class OAuthUrisExtension {

        @Test
        @DisplayName("oauth-uris extension is added to the first REST security element")
        void extensionIsAdded() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);

            List<Extension> extensions =
                    cs.getRest().get(0).getSecurity().getExtensionsByUrl(OAUTH_URIS_URL);
            assertThat(extensions).hasSize(1);
        }

        @Test
        @DisplayName("authorize sub-extension points to Keycloak authorization endpoint")
        void authorizeSubExtension() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);

            Extension oauthUris = cs.getRest().get(0).getSecurity()
                    .getExtensionByUrl(OAUTH_URIS_URL);
            Extension authorize = oauthUris.getExtensionByUrl("authorize");

            assertThat(authorize).isNotNull();
            assertThat(authorize.getValue().primitiveValue())
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/auth");
        }

        @Test
        @DisplayName("token sub-extension points to Keycloak token endpoint")
        void tokenSubExtension() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);

            Extension oauthUris = cs.getRest().get(0).getSecurity()
                    .getExtensionByUrl(OAUTH_URIS_URL);
            Extension token = oauthUris.getExtensionByUrl("token");

            assertThat(token).isNotNull();
            assertThat(token.getValue().primitiveValue())
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/token");
        }

        @Test
        @DisplayName("introspect sub-extension is present (recommended)")
        void introspectSubExtension() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);

            Extension oauthUris = cs.getRest().get(0).getSecurity()
                    .getExtensionByUrl(OAUTH_URIS_URL);
            Extension introspect = oauthUris.getExtensionByUrl("introspect");

            assertThat(introspect).isNotNull();
            assertThat(introspect.getValue().primitiveValue())
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/token/introspect");
        }

        @Test
        @DisplayName("revoke sub-extension is present (recommended)")
        void revokeSubExtension() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);

            Extension oauthUris = cs.getRest().get(0).getSecurity()
                    .getExtensionByUrl(OAUTH_URIS_URL);
            Extension revoke = oauthUris.getExtensionByUrl("revoke");

            assertThat(revoke).isNotNull();
            assertThat(revoke.getValue().primitiveValue())
                    .isEqualTo(KC_BASE + "/protocol/openid-connect/revoke");
        }

        @Test
        @DisplayName("Non-standard 'certs' sub-extension is NOT present")
        void certsSubExtensionIsAbsent() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);

            Extension oauthUris = cs.getRest().get(0).getSecurity()
                    .getExtensionByUrl(OAUTH_URIS_URL);
            assertThat(oauthUris.getExtensionByUrl("certs")).isNull();
        }

        @Test
        @DisplayName("Non-standard space-separated 'capabilities' sub-extension is NOT present")
        void capabilitiesSubExtensionIsAbsent() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);

            Extension oauthUris = cs.getRest().get(0).getSecurity()
                    .getExtensionByUrl(OAUTH_URIS_URL);
            assertThat(oauthUris.getExtensionByUrl("capabilities")).isNull();
        }
    }

    @Nested
    @DisplayName("Guard conditions")
    class GuardConditions {

        @Test
        @DisplayName("Does not modify CapabilityStatement when OAuth2 is disabled")
        void doesNotModifyWhenOauth2Disabled() {
            // Disable SMART mode
            ReflectionTestUtils.setField(interceptor, "shouldSecureEndpoints", false);
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);

            assertThat(cs.getRest().get(0).getSecurity().getExtension()).isEmpty();
        }

        @Test
        @DisplayName("Does not throw when CapabilityStatement is null")
        void doesNotThrowForNull() {
            assertThatCode(() -> interceptor.customize(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Does not throw when CapabilityStatement has empty REST list")
        void doesNotThrowForEmptyRest() {
            CapabilityStatement cs = new CapabilityStatement();
            assertThatCode(() -> interceptor.customize(cs)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Security block metadata")
    class SecurityBlock {

        @Test
        @DisplayName("CORS flag is set to true")
        void corsIsEnabled() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);
            assertThat(cs.getRest().get(0).getSecurity().getCors()).isTrue();
        }

        @Test
        @DisplayName("Security description is set")
        void descriptionIsSet() {
            CapabilityStatement cs = minimalCapabilityStatement();
            interceptor.customize(cs);
            assertThat(cs.getRest().get(0).getSecurity().getDescription()).isNotBlank();
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private CapabilityStatement minimalCapabilityStatement() {
        CapabilityStatement cs = new CapabilityStatement();
        CapabilityStatement.CapabilityStatementRestComponent rest =
                new CapabilityStatement.CapabilityStatementRestComponent();
        rest.setSecurity(new CapabilityStatement.CapabilityStatementRestSecurityComponent());
        cs.addRest(rest);
        return cs;
    }
}




