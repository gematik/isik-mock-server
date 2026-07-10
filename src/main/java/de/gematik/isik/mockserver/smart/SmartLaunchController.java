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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Initiates an EHR Launch flow as defined by the SMART App Launch v2.2.0 specification.
 *
 * <p>An EHR (or any server-side orchestrator) calls {@code GET /fhir/launch} with optional {@code
 * patient} and {@code encounter} query parameters. This endpoint:
 *
 * <ol>
 *   <li>Creates a short-lived opaque launch handle in the {@link SmartLaunchContextStore} that
 *       binds the supplied EHR context to a UUID.
 *   <li>Redirects the caller to the Keycloak authorization endpoint with the launch handle as the
 *       {@code launch} parameter and the FHIR base URL as the {@code iss} parameter.
 * </ol>
 *
 * <p>The app then presents this redirect to the end user. The user logs in via Keycloak, and — once
 * a token enrichment mechanism (e.g. a Keycloak protocol mapper or a token proxy) is in place — the
 * resulting access token will carry {@code patient} / {@code encounter} context claims derived from
 * the launch handle.
 *
 * <p>This endpoint is publicly accessible (no Bearer token required) because the EHR itself
 * initiates the flow before the user has authenticated.
 */
@ConditionalOnBooleanProperty(name = {"spring.security.oauth2.enable"})
@RestController
public class SmartLaunchController {
	private final SmartConfigProperties smartConfigProperties;
	private final SmartLaunchContextStore launchContextStore;

	public SmartLaunchController(
			final SmartConfigProperties smartConfigProperties, final SmartLaunchContextStore launchContextStore) {
		this.smartConfigProperties = smartConfigProperties;
		this.launchContextStore = launchContextStore;
	}

	/**
	 * Initiates an EHR App Launch.
	 *
	 * @param patient optional FHIR Patient logical ID to bind to this launch
	 * @param encounter optional FHIR Encounter logical ID to bind to this launch
	 * @return HTTP 302 redirect to the Keycloak authorization endpoint with {@code launch} + {@code
	 *     iss} parameters
	 */
	@GetMapping("/fhir/launch")
	public ResponseEntity<Void> initiateLaunch(
			@RequestParam(required = false) final String patient,
			@RequestParam(required = false) final String encounter) {

		String launchHandle = launchContextStore.create(patient, encounter);

		URI redirectUri = UriComponentsBuilder.fromUriString(smartConfigProperties.authorizationEndpoint())
				.queryParam("launch", launchHandle)
				.queryParam("iss", smartConfigProperties.getFhirBaseUrl())
				.queryParam("scope", "launch openid fhirUser")
				.queryParam("response_type", "code")
				.build()
				.toUri();

		return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
	}
}
