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

import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilderRuleOp;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilderRuleOpClassifier;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleTester;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import de.gematik.isik.mockserver.smart.SmartAuthorizationAccessType;
import de.gematik.isik.mockserver.smart.SmartScope;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Interceptor based on HAPI FHIR {@link AuthorizationInterceptor} that enforces SMART on FHIR STU
 * 2.2 scopes.
 *
 * <p>When the property {@code spring.security.oauth2.enable} is {@code false} (default), the
 * interceptor allows all requests so that the server can be used without OAuth2. When enabled, the
 * JWT {@code scope} claim is parsed and translated into HAPI FHIR authorization rules.
 *
 * <p>Supported scope format: {@code (patient|user|system)/(ResourceType|*).c?r?u?d?s?}
 */
@Slf4j
@Component
@Interceptor
public class SmartAuthorizationInterceptor extends AuthorizationInterceptor {
	public static final String RESOURCE_PATIENT = "Patient";

	/**
	 * OIDC / SMART non-resource scopes that must be silently skipped during FHIR authorization rule
	 * building. Their presence in a token is valid and expected (e.g., every token issued by Keycloak
	 * typically includes {@code openid}), but they grant no FHIR resource permissions and must not
	 * count against {@code hasValidScopes}.
	 */
	private static final Set<String> NON_FHIR_SCOPES =
			Set.of("openid", "profile", "fhirUser", "offline_access", "online_access", "launch");

	@Value("${spring.security.oauth2.enable:false}")
	private boolean shouldSecureEndpoints;

	@Override
	public List<IAuthRule> buildRuleList(final RequestDetails theRequestDetails) {
		if (!shouldSecureEndpoints) {
			return new RuleBuilder()
					.allowAll("OAuth2 disabled – allow all requests")
					.build();
		}

		// The CapabilityStatement endpoint must always be publicly readable, even for anonymous
		// (unauthenticated) requests. Placing this rule first ensures it is matched before any
		// deny rules that follow for unauthenticated or unpermitted requests.
		final List<IAuthRule> rules = new ArrayList<>(
				new RuleBuilder().allow("metadata").metadata().andThen().build());

		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null
				|| !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			log.debug("Denying unauthenticated FHIR request");
			rules.addAll(new RuleBuilder().denyAll("Unauthenticated request").build());
			return rules;
		}

		final Jwt jwt = extractJwt(authentication);
		if (jwt == null) {
			log.warn("Authentication credentials are not a JWT – denying request");
			return new RuleBuilder().denyAll("Missing JWT credentials").build();
		}

		final String scopeString = jwt.getClaimAsString("scope");
		if (scopeString == null || scopeString.isBlank()) {
			log.debug("JWT contains no 'scope' claim – denying request");
			return new RuleBuilder().denyAll("No scopes in token").build();
		}

		// Read patient context directly from the JWT claim.
		final String patientId = jwt.getClaimAsString(RESOURCE_PATIENT.toLowerCase(Locale.ROOT));

		boolean hasValidScopes = false;
		for (String rawScope : scopeString.trim().split("\\s+")) {
			// Explicitly skip well-known OIDC / SMART non-resource scopes.
			// launch/* context scopes (e.g. "launch/patient") are also skipped.
			if (NON_FHIR_SCOPES.contains(rawScope) || rawScope.startsWith("launch/")) {
				log.debug("Skipping non-FHIR OIDC scope: {}", rawScope);
				continue;
			}
			Optional<SmartScope> parsed = SmartScope.parse(rawScope);
			if (parsed.isEmpty()) {
				log.debug("Ignoring unrecognised scope token: {}", rawScope);
				continue;
			}
			addRulesForScope(rules, parsed.get(), patientId);
			hasValidScopes = true;
		}

		if (!hasValidScopes) {
			log.debug("No valid SMART scopes found in token – denying request");
			return new RuleBuilder().denyAll("No valid SMART scopes").build();
		}

		// Default deny – must appear last.
		rules.addAll(
				new RuleBuilder().denyAll("No SMART scope permits this request").build());
		return rules;
	}

	private void addRulesForScope(final List<IAuthRule> rules, final SmartScope scope, final String patientId) {

		if (scope.canRead() || scope.canSearch()) {
			rules.addAll(buildReadRules(scope, patientId));
		}
		if (scope.canCreate()) {
			rules.addAll(buildCreateRules(scope, patientId));
		}
		if (scope.canUpdate()) {
			rules.addAll(buildUpdateRules(scope, patientId));
		}
		if (scope.canDelete()) {
			rules.addAll(buildDeleteRules(scope, patientId));
		}
	}

	/**
	 * Builds read rules for the given scope. HAPI FHIR's {@code read()} covers reads, vreads,
	 * history, and searches.
	 *
	 * <p>For {@code patient}-context scopes with a {@code patient} JWT claim two rules are emitted:
	 *
	 * <ol>
	 *   <li>A {@code withAnyId()} rule gated by an {@link IAuthRuleTester} that fires <em>only</em>
	 *       for {@code SEARCH_TYPE} operations. This allows plain searches (e.g. {@code GET
	 *       /fhir/Patient}) while deferring compartment narrowing to HAPI FHIR's query layer.
	 *   <li>An {@code inCompartment} rule that allows reads of specific resources (e.g. {@code GET
	 *       /fhir/Patient/{id}}) only when the requested resource belongs to the patient compartment.
	 * </ol>
	 *
	 * <p>Without the first rule, a plain search abstains at the pre-handle pointcut (no resource ID
	 * is known yet for compartment checking) and the default-deny fires immediately.
	 */
	private List<IAuthRule> buildReadRules(final SmartScope scope, final String patientId) {
		final String ruleName = "smart-read:" + scope.accessType() + "/" + scope.resourceType();

		boolean useCompartment =
				scope.accessType() == SmartAuthorizationAccessType.PATIENT && patientId != null && !patientId.isBlank();

		if (useCompartment) {
			final IIdType compartmentId = new IdType(RESOURCE_PATIENT, patientId);
			List<IAuthRule> result = new ArrayList<>();
			// Rule 1: search allow (tester ensures this rule is skipped for instance reads).
			result.addAll(buildSearchRules(scope, ruleName));
			// Rule 2: compartment read (allows own record, abstains for others → denyAll fires).
			result.addAll(buildInCompartmentRules(scope, ruleName, compartmentId));

			return result;
		}

		// Non-patient scope: allow all reads/searches on the resource type with any ID.
		final IAuthRuleBuilderRuleOpClassifier classifier = scope.isWildcard()
				? new RuleBuilder().allow(ruleName).read().allResources()
				: new RuleBuilder().allow(ruleName).read().resourcesOfType(scope.resourceType());
		return classifier.withAnyId().build();
	}

	/**
	 * Search rules (tester ensures this rule is skipped for instance reads).
	 *
	 * @param scope the current {@link SmartScope} instance
	 * @param ruleName the name of the rule to be used
	 * @return a list of {@link IAuthRule}
	 */
	private List<IAuthRule> buildSearchRules(final SmartScope scope, final String ruleName) {
		final IAuthRuleBuilderRuleOpClassifier searchClassifier = scope.isWildcard()
				? new RuleBuilder().allow(ruleName + "-search").read().allResources()
				: new RuleBuilder().allow(ruleName + "-search").read().resourcesOfType(scope.resourceType());
		return searchClassifier
				.withAnyId()
				.withTester(new IAuthRuleTester() {
					@Override
					public boolean matches(final RuleTestRequest theRequest) {
						return theRequest.operation == RestOperationTypeEnum.SEARCH_TYPE;
					}
				})
				.build();
	}

	/**
	 * Compartment read rules (allows own record, abstains for others → denyAll fires).
	 *
	 * @param scope the current {@link SmartScope} instance
	 * @param ruleName the name of the rule to be used
	 * @param compartmentId the id of the resource to be accessed
	 * @return a list of {@link IAuthRule}
	 */
	private List<IAuthRule> buildInCompartmentRules(
			final SmartScope scope, final String ruleName, final IIdType compartmentId) {
		IAuthRuleBuilderRuleOpClassifier readClassifier = scope.isWildcard()
				? new RuleBuilder().allow(ruleName).read().allResources()
				: new RuleBuilder().allow(ruleName).read().resourcesOfType(scope.resourceType());
		return readClassifier.inCompartment(RESOURCE_PATIENT, compartmentId).build();
	}

	/**
	 * Builds create rules for the given scope. Uses HAPI's {@code create()} which restricts to HTTP
	 * POST create interactions. For patient-level scopes, the resource body is checked against the
	 * patient compartment.
	 */
	private List<IAuthRule> buildCreateRules(final SmartScope scope, final String patientId) {
		String ruleName = "smart-create:" + scope.accessType() + "/" + scope.resourceType();
		IAuthRuleBuilderRuleOp op = new RuleBuilder().allow(ruleName).create();
		// For create we always use withAnyId() because the resource does not yet exist;
		// the patient compartment check would operate on the resource body if available.
		return applyClassifier(op, scope, patientId, true);
	}

	/**
	 * Builds update (and patch) rules for the given scope. HAPI's {@code write()} covers UPDATE and
	 * PATCH operations (as well as transaction bundles).
	 */
	private List<IAuthRule> buildUpdateRules(final SmartScope scope, final String patientId) {
		String ruleName = "smart-update:" + scope.accessType() + "/" + scope.resourceType();
		IAuthRuleBuilderRuleOp op = new RuleBuilder().allow(ruleName).write();
		return applyClassifier(op, scope, patientId, false);
	}

	/** Builds delete rules for the given scope. */
	private List<IAuthRule> buildDeleteRules(final SmartScope scope, final String patientId) {
		String ruleName = "smart-delete:" + scope.accessType() + "/" + scope.resourceType();
		IAuthRuleBuilderRuleOp op = new RuleBuilder().allow(ruleName).delete();
		return applyClassifier(op, scope, patientId, false);
	}

	/**
	 * Applies the resource type classifier and, where appropriate, a patient compartment restriction
	 * to the supplied operation builder.
	 *
	 * @param op the operation builder step
	 * @param scope the parsed SMART scope
	 * @param patientId the patient ID from the JWT {@code patient} claim (may be {@code null})
	 * @param forceAnyId when {@code true} the compartment constraint is skipped (used for creates)
	 */
	private List<IAuthRule> applyClassifier(
			final IAuthRuleBuilderRuleOp op, final SmartScope scope, final String patientId, final boolean forceAnyId) {

		IAuthRuleBuilderRuleOpClassifier classifier =
				scope.isWildcard() ? op.allResources() : op.resourcesOfType(scope.resourceType());

		boolean useCompartment = !forceAnyId
				&& scope.accessType() == SmartAuthorizationAccessType.PATIENT
				&& patientId != null
				&& !patientId.isBlank();

		if (useCompartment) {
			IIdType compartmentId = new IdType(RESOURCE_PATIENT, patientId);
			return classifier.inCompartment(RESOURCE_PATIENT, compartmentId).build();
		}

		return classifier.withAnyId().build();
	}

	/**
	 * Extracts the {@link Jwt} from the current {@link Authentication}.
	 *
	 * <p>Preferred path: {@link AbstractOAuth2TokenAuthenticationToken} (e.g. {@link
	 * org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}) —
	 * the JWT is stored as the OAuth2 token and is never erased by {@code ProviderManager}. Fallback
	 * path: raw {@code getCredentials()} for backwards compatibility.
	 */
	@Nullable
	private Jwt extractJwt(final Authentication authentication) {
		if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> oauthToken
				&& oauthToken.getToken() instanceof Jwt jwt) {
			return jwt;
		}
		if (authentication.getCredentials() instanceof Jwt jwt) {
			return jwt;
		}
		return null;
	}
}
