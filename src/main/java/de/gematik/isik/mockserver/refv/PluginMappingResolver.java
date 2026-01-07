package de.gematik.isik.mockserver.refv;

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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PluginMappingResolver {

	private final PluginMappingLoader pluginMappingLoader;

	public List<String> getPluginIdsFromResourceType(String resourceType) {
		Map<String, List<String>> pluginIdMap = pluginMappingLoader.getResourceTypeToPluginIdMap();

		for (Map.Entry<String, List<String>> entry : pluginIdMap.entrySet()) {
			if (entry.getKey().equals(resourceType)) {
				return entry.getValue();
			}
		}

		return List.of();
	}

	public List<String> getProfileUrlsFromResourceType(String resourceType) {
		Map<String, List<String>> profileUrlMap = pluginMappingLoader.getResourceTypeToProfileUrlMap();

		for (Map.Entry<String, List<String>> entry : profileUrlMap.entrySet()) {
			if (entry.getKey().equals(resourceType)) {
				return entry.getValue();
			}
		}

		return List.of();
	}

	public String getPluginIdFromProfile(String profileUrl) {
		Map<String, List<String>> profilesMap = pluginMappingLoader.getProfileUrlToPluginIdMap();

		for (Map.Entry<String, List<String>> entry : profilesMap.entrySet()) {
			if (entry.getValue().contains(profileUrl)) {
				return entry.getKey();
			}
		}

		return null;
	}
}
