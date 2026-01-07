package de.gematik.isik.mockserver.operation;

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

import ca.uhn.fhir.rest.api.server.IBundleProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This class provides a helper method to produce a dummy IBundleProvider returning the given resources.
 * The IBundleProvider interface expects methods to return List<IBaseResource> and other specific types.
 */
public class SimpleBundleProvider {
	public static IBundleProvider createBundleProvider(List<IBaseResource> encounters) {
		return new IBundleProvider() {
			@Override
			public @NotNull List<IBaseResource> getResources(int fromIndex, int toIndex) {
				return new ArrayList<>(encounters.subList(fromIndex, Math.min(toIndex, encounters.size())));
			}

			@Override
			public Integer size() {
				return encounters.size();
			}

			@Override
			public Integer preferredPageSize() {
				return null;
			}

			@Override
			public String getUuid() {
				return null;
			}

			@Override
			public IPrimitiveType<Date> getPublished() {
				return null;
			}
		};
	}
}
