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

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Defines the Authorization Level. */
public enum SmartAuthorizationAccessLevel {
	NONE,
	CREATE,
	READ,
	UPDATE,
	DELETE,
	SEARCH;

	public static List<SmartAuthorizationAccessLevel> fromScope(@NonNull final String scope) {
		List<SmartAuthorizationAccessLevel> authorizations = new ArrayList<>();
		if (scope.contains("r")) {
			authorizations.add(READ);
		}
		if (scope.contains("c")) {
			authorizations.add(CREATE);
		}
		if (scope.contains("u")) {
			authorizations.add(UPDATE);
		}
		if (scope.contains("d")) {
			authorizations.add(DELETE);
		}
		if (scope.contains("s")) {
			authorizations.add(SEARCH);
		}
		// Fallback: no authorization
		if (authorizations.isEmpty()) {
			authorizations.add(NONE);
		}

		return authorizations;
	}
}
