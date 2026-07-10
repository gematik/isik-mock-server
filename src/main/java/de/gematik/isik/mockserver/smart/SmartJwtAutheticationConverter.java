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

import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Converts a Spring Security {@link Jwt} into a {@link JwtAuthenticationToken} whose authorities
 * are derived from the JWT {@code scope} claim.
 *
 * <p>{@link JwtAuthenticationToken} extends {@code AbstractOAuth2TokenAuthenticationToken<Jwt>} and
 * stores the {@link Jwt} as both principal and credentials. Because {@code
 * AbstractOAuth2TokenAuthenticationToken.eraseCredentials()} is a no-op for OAuth2 token types,
 * Spring Security's {@code ProviderManager} cannot accidentally nullify the token when {@code
 * eraseCredentialsAfterAuthentication} is {@code true} (the default).
 *
 * <p>The {@link SmartPrincipal} derived from the JWT claims is stored in the token's {@code
 * details} field so that downstream components can access patient/encounter context without having
 * to reparse the JWT.
 *
 * <p>Each whitespace-separated scope token is mapped to a {@link SimpleGrantedAuthority} using the
 * {@code SCOPE_} prefix (e.g. {@code patient/*.rs} → {@code SCOPE_patient/*.rs}).
 */
@Component
@ConditionalOnProperty(
		name = {"spring.security.oauth2.enable"},
		havingValue = "true")
public class SmartJwtAutheticationConverter
		implements org.springframework.core.convert.converter.Converter<Jwt, JwtAuthenticationToken> {

	@Override
	public JwtAuthenticationToken convert(final @NonNull Jwt jwt) {
		final Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
		final SmartPrincipal principal = SmartPrincipal.fromJwt(jwt);

		final JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities);
		// Make the SmartPrincipal available to downstream consumers (e.g. SmartConfigController).
		token.setDetails(principal);
		return token;
	}

	/**
	 * Derives Spring Security {@link GrantedAuthority} objects from the JWT {@code scope} claim.
	 *
	 * <p>The {@code scope} claim is expected to be a space-separated list of SMART on FHIR scope
	 * tokens. Each token is prefixed with {@code SCOPE_} following the Spring Security OAuth2
	 * convention.
	 *
	 * @param jwt the decoded JWT
	 * @return the collection of granted authorities; empty if the claim is absent
	 */
	private Collection<GrantedAuthority> extractAuthorities(final Jwt jwt) {
		String scopeClaim = jwt.getClaimAsString("scope");
		if (scopeClaim == null || scopeClaim.isBlank()) {
			return Collections.emptyList();
		}

		return Arrays.stream(scopeClaim.trim().split("\\s+"))
				.filter(s -> !s.isBlank())
				.map(scope -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + scope))
				.toList();
	}
}
