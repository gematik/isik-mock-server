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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PluginMappingLoader {

	private static final String SUCCESS_MESSAGE =
			"Successfully mapped validation module plugin information from config file: {}";

	private final String jsonFilePathResourceTypeToPluginId;
	private final String jsonFilePathResourceTypeToProfileUrl;
	private final String jsonFilePathProfileUrlToPluginId;
	private final ObjectMapper objectMapper;

	public PluginMappingLoader(
			@Value("${plugins.resourcetype2pluginid}") String jsonFilePathResourceTypeToPluginId,
			@Value("${plugins.resourcetype2profileurl}") String jsonFilePathResourceTypeToProfileUrl,
			@Value("${plugins.profileurl2pluginid}") String jsonFilePathProfileUrlToPluginId,
			ObjectMapper objectMapper) {
		this.jsonFilePathResourceTypeToPluginId = jsonFilePathResourceTypeToPluginId;
		this.jsonFilePathResourceTypeToProfileUrl = jsonFilePathResourceTypeToProfileUrl;
		this.jsonFilePathProfileUrlToPluginId = jsonFilePathProfileUrlToPluginId;
		this.objectMapper = objectMapper;
	}

	@Getter
	private Map<String, List<String>> resourceTypeToPluginIdMap;

	@Getter
	private Map<String, List<String>> resourceTypeToProfileUrlMap;

	@Getter
	private Map<String, List<String>> profileUrlToPluginIdMap;

	@PostConstruct
	public void loadData() throws IOException {
		var jsonFileResourceTypeToPluginId =
				getClass().getClassLoader().getResourceAsStream(jsonFilePathResourceTypeToPluginId);
		resourceTypeToPluginIdMap = objectMapper.readValue(jsonFileResourceTypeToPluginId, Map.class);
		log.info(SUCCESS_MESSAGE, jsonFilePathResourceTypeToPluginId);

		var jsonFileResourceTypeToProfileUrl =
				getClass().getClassLoader().getResourceAsStream(jsonFilePathResourceTypeToProfileUrl);
		resourceTypeToProfileUrlMap = objectMapper.readValue(jsonFileResourceTypeToProfileUrl, Map.class);
		log.info(SUCCESS_MESSAGE, jsonFilePathResourceTypeToProfileUrl);

		var jsonFileProfileUrlToPluginId =
				getClass().getClassLoader().getResourceAsStream(jsonFilePathProfileUrlToPluginId);
		profileUrlToPluginIdMap = objectMapper.readValue(jsonFileProfileUrlToPluginId, Map.class);
		log.info(SUCCESS_MESSAGE, jsonFilePathProfileUrlToPluginId);
	}
}
