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

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PluginMappingResolverTest {

	@InjectMocks
	private PluginMappingResolver pluginMappingResolver;

	@Mock
	private PluginMappingLoader pluginMappingLoader;

	private AutoCloseable mocks;

	private final Map<String, List<String>> mockResourceTypeToPluginIdMap = Map.of(
		"ResourceTypeA", List.of("PluginId1", "PluginId2"),
		"ResourceTypeB", List.of("PluginId3")
	);
	private final Map<String, List<String>> mockResourceTypeToProfileUrlMap = Map.of(
		"ResourceTypeA", List.of("ProfileUrl1", "ProfileUrl2"),
		"ResourceTypeC", List.of("ProfileUrl3")
	);
	private final Map<String, List<String>> mockProfileUrlToPluginIdMap = Map.of(
		"PluginId1", List.of("ProfileUrl1"),
		"PluginId2", List.of("ProfileUrl2"),
		"PluginId3", List.of("ProfileUrl3")
	);

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);

		when(pluginMappingLoader.getResourceTypeToPluginIdMap()).thenReturn(mockResourceTypeToPluginIdMap);
		when(pluginMappingLoader.getResourceTypeToProfileUrlMap()).thenReturn(mockResourceTypeToProfileUrlMap);
		when(pluginMappingLoader.getProfileUrlToPluginIdMap()).thenReturn(mockProfileUrlToPluginIdMap);
	}

	@AfterEach
	@SneakyThrows
	void tearDown() {
		if (mocks != null) {
			mocks.close();
		}
	}

	@Test
	void testGetPluginIdsFromResourceType() {
		List<String> pluginIdsForResourceTypeA = pluginMappingResolver.getPluginIdsFromResourceType("ResourceTypeA");
		assertThat(pluginIdsForResourceTypeA).containsExactly("PluginId1", "PluginId2");

		List<String> pluginIdsForResourceTypeB = pluginMappingResolver.getPluginIdsFromResourceType("ResourceTypeB");
		assertThat(pluginIdsForResourceTypeB).containsExactly("PluginId3");

		List<String> pluginIdsForUnknownResourceType = pluginMappingResolver.getPluginIdsFromResourceType("UnknownType");
		assertThat(pluginIdsForUnknownResourceType).isEmpty();
	}

	@Test
	void testGetProfileUrlsFromResourceType() {
		List<String> profileUrlsForResourceTypeA = pluginMappingResolver.getProfileUrlsFromResourceType("ResourceTypeA");
		assertThat(profileUrlsForResourceTypeA).containsExactly("ProfileUrl1", "ProfileUrl2");

		List<String> profileUrlsForResourceTypeC = pluginMappingResolver.getProfileUrlsFromResourceType("ResourceTypeC");
		assertThat(profileUrlsForResourceTypeC).containsExactly("ProfileUrl3");

		List<String> profileUrlsForUnknownResourceType = pluginMappingResolver.getProfileUrlsFromResourceType("UnknownType");
		assertThat(profileUrlsForUnknownResourceType).isEmpty();
	}

	@Test
	void testGetPluginIdFromProfile() {
		String pluginIdForProfileUrl1 = pluginMappingResolver.getPluginIdFromProfile("ProfileUrl1");
		assertThat(pluginIdForProfileUrl1).isEqualTo("PluginId1");

		String pluginIdForProfileUrl3 = pluginMappingResolver.getPluginIdFromProfile("ProfileUrl3");
		assertThat(pluginIdForProfileUrl3).isEqualTo("PluginId3");

		String pluginIdForUnknownProfileUrl = pluginMappingResolver.getPluginIdFromProfile("UnknownProfileUrl");
		assertThat(pluginIdForUnknownProfileUrl).isNull();
	}
}
