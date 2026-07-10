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

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import de.gematik.isik.mockserver.smart.SmartConfigProperties;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.UriType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HAPI FHIR Interceptor that annotates the CapabilityStatement with SMART on FHIR OAuth URI
 * extensions per <a
 * href="http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris">the oauth-uris
 * StructureDefinition</a>.
 *
 * <p>Only the standardised sub-extensions ({@code authorize}, {@code token}, {@code introspect},
 * {@code revoke}) are added. Non-standard sub-extensions like {@code certs} or the space-separated
 * {@code capabilities} string that appeared in the pre-v2 implementation have been removed.
 */
@Component
@Interceptor
public class SmartCapabilityStatementInterceptor {

	@Value("${spring.security.oauth2.enable:false}")
	private boolean shouldSecureEndpoints;

	private final SmartConfigProperties smartConfigProperties;

	public SmartCapabilityStatementInterceptor(final SmartConfigProperties smartConfigProperties) {
		this.smartConfigProperties = smartConfigProperties;
	}

	@Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
	public void customize(final CapabilityStatement capabilityStatement) {
		if (!shouldSecureEndpoints) {
			return;
		}

		if (capabilityStatement == null
				|| capabilityStatement.getRest() == null
				|| capabilityStatement.getRest().isEmpty()) {
			return;
		}

		// SMART on FHIR OAuth URIs Extension (SMART App Launch v2.2.0)
		String smartExtensionUrl = "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris";

		Extension smartExtension = new Extension(smartExtensionUrl);

		// Authorization Endpoint (required)
		Extension authorizeExtension = new Extension("authorize");
		authorizeExtension.setValue(new UriType(smartConfigProperties.authorizationEndpoint()));
		smartExtension.addExtension(authorizeExtension);

		// Token Endpoint (required)
		Extension tokenExtension = new Extension("token");
		tokenExtension.setValue(new UriType(smartConfigProperties.tokenEndpoint()));
		smartExtension.addExtension(tokenExtension);

		// Introspection Endpoint (recommended)
		Extension introspectExtension = new Extension("introspect");
		introspectExtension.setValue(new UriType(smartConfigProperties.introspectionEndpoint()));
		smartExtension.addExtension(introspectExtension);

		// Revocation Endpoint (recommended)
		Extension revokeExtension = new Extension("revoke");
		revokeExtension.setValue(new UriType(smartConfigProperties.revocationEndpoint()));
		smartExtension.addExtension(revokeExtension);

		capabilityStatement.getRest().getFirst().getSecurity().addExtension(smartExtension);

		capabilityStatement.getRest().getFirst().getSecurity().setCors(true);
		capabilityStatement.getRest().getFirst().getSecurity().setDescription("SMART on FHIR with OAuth2");
	}
}
