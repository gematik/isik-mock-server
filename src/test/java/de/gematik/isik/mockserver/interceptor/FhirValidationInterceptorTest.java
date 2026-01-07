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

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.isik.mockserver.helper.ReusableRequestWrapper;
import de.gematik.isik.mockserver.refv.PluginLoader;
import de.gematik.isik.mockserver.refv.PluginMappingLoader;
import de.gematik.isik.mockserver.refv.PluginMappingResolver;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static de.gematik.isik.mockserver.helper.ResourceLoadingHelper.loadResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;

class FhirValidationInterceptorTest {
	private FhirValidationInterceptor interceptor;
	private String validResource;
	private String invalidResource;

	@BeforeEach
	@SneakyThrows
	void setup() {
		FhirContext fhirContext = FhirContext.forR4();
		String resourceTypeToPluginIdPath = "mockResourceTypeToPluginId.json";
		String resourceTypeToProfileUrlPath = "mockResourceTypeToProfileUrl.json";
		String profileUrlToPluginIdPath = "mockProfileUrlToPluginId.json";

		FhirValidationHandler fhirValidationHandler = getFhirValidationHandler(resourceTypeToPluginIdPath, resourceTypeToProfileUrlPath, profileUrlToPluginIdPath);
		interceptor = new FhirValidationInterceptor(fhirValidationHandler, fhirContext);

		validResource = loadResourceAsString("fhir-examples/valid/valid-resource.json");
		invalidResource = loadResourceAsString("fhir-examples/invalid/invalid-resource.json");
	}

	@NotNull
	private static FhirValidationHandler getFhirValidationHandler(String resourceTypeToPluginIdPath, String resourceTypeToProfileUrlPath, String profileUrlToPluginIdPath) throws IOException {
		PluginMappingLoader pluginMappingLoader = new PluginMappingLoader(resourceTypeToPluginIdPath, resourceTypeToProfileUrlPath, profileUrlToPluginIdPath, new ObjectMapper());
		pluginMappingLoader.loadData();

		PluginMappingResolver pluginMappingResolver = new PluginMappingResolver(pluginMappingLoader);
		PluginLoader pluginLoader = new PluginLoader("plugins", true);
		pluginLoader.init();

		return new FhirValidationHandler(pluginMappingResolver, pluginLoader, new FhirValidationBundleHandler());
	}

	@Test
	@SneakyThrows
	void testIncomingRequestPreProcessed_validResource() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.setMethod("POST");
		request.setPathInfo("/fhir/Patient");
		request.setContent(validResource.getBytes());
		request.setContentType("application/fhir+json");

		boolean result = interceptor.incomingRequestPreProcessed(new ReusableRequestWrapper(request), response);

		assertThat(result).isTrue();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	@SneakyThrows
	void testIncomingRequestPreProcessed_invalidResource() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.setMethod("POST");
		request.setPathInfo("/Patient");
		request.setContent(invalidResource.getBytes());
		request.setContentType("application/fhir+json");

		boolean result = interceptor.incomingRequestPreProcessed(new ReusableRequestWrapper(request), response);

		assertThat(result).isFalse();
		assertThat(response.getStatus()).isEqualTo(400);
	}
}
