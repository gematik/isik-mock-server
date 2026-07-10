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

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a parsed SMART on FHIR STU 2.2 scope, e.g.: {@code patient/Observation.rs}, {@code
 * user/*.cruds}, {@code system/Patient.r}
 *
 * <p>Scope format: {@code (patient|user|system)/(ResourceType|*).c?r?u?d?s?(?param=value)*}
 */
public record SmartScope(
		SmartAuthorizationAccessType accessType, String resourceType, Set<SmartAuthorizationAccessLevel> permissions) {

	/**
	 * Pattern matching the SMART on FHIR scope grammar. Group 1: context (patient | user | system)
	 * Group 2: resource type or wildcard Group 3: permission letters (any combination of c, r, u, d,
	 * s)
	 */
	private static final Pattern SCOPE_PATTERN =
			Pattern.compile("^(patient|user|system)/([A-Za-z*]+)\\.([cruds]+)(?:\\?.*)?$");

	/**
	 * Attempts to parse a raw scope string into a {@link SmartScope}.
	 *
	 * @param scope the raw scope string from the JWT {@code scope} claim
	 * @return an {@link Optional} containing the parsed scope, or empty if the format is not
	 *     recognized
	 */
	public static Optional<SmartScope> parse(final String scope) {
		if (scope == null || scope.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = SCOPE_PATTERN.matcher(scope.trim());
		if (!matcher.matches()) {
			return Optional.empty();
		}

		SmartAuthorizationAccessType accessType =
				switch (matcher.group(1)) {
					case "patient" -> SmartAuthorizationAccessType.PATIENT;
					case "user" -> SmartAuthorizationAccessType.USER;
					case "system" -> SmartAuthorizationAccessType.SYSTEM;
					default -> null;
				};

		if (accessType == null) {
			return Optional.empty();
		}

		String resourceType = matcher.group(2);
		Set<SmartAuthorizationAccessLevel> permissions =
				EnumSet.copyOf(SmartAuthorizationAccessLevel.fromScope(matcher.group(3)));

		return Optional.of(new SmartScope(accessType, resourceType, permissions));
	}

	/** Returns {@code true} if this scope applies to all resource types ({@code *}). */
	public boolean isWildcard() {
		return "*".equals(resourceType);
	}

	/** Returns {@code true} if the {@code r} (read) permission is granted. */
	public boolean canRead() {
		return permissions.contains(SmartAuthorizationAccessLevel.READ);
	}

	/** Returns {@code true} if the {@code s} (search) permission is granted. */
	public boolean canSearch() {
		return permissions.contains(SmartAuthorizationAccessLevel.SEARCH);
	}

	/** Returns {@code true} if the {@code c} (create) permission is granted. */
	public boolean canCreate() {
		return permissions.contains(SmartAuthorizationAccessLevel.CREATE);
	}

	/** Returns {@code true} if the {@code u} (update) permission is granted. */
	public boolean canUpdate() {
		return permissions.contains(SmartAuthorizationAccessLevel.UPDATE);
	}

	/** Returns {@code true} if the {@code d} (delete) permission is granted. */
	public boolean canDelete() {
		return permissions.contains(SmartAuthorizationAccessLevel.DELETE);
	}
}
