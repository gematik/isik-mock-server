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
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.isik.mockserver.refv.PluginLoader;
import de.gematik.isik.mockserver.refv.PluginMappingLoader;
import de.gematik.isik.mockserver.refv.PluginMappingResolver;
import de.gematik.refv.commons.validation.ValidationResult;
import lombok.SneakyThrows;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static de.gematik.isik.mockserver.helper.ResourceLoadingHelper.loadResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;

class FhirValidationHandlerTest {

	private FhirValidationHandler fhirValidationHandler;
	private FhirValidationHandler fhirValidationHandlerDisabledPlugins;
	private IParser parser;

	@BeforeEach
	@SneakyThrows
	void setUp() {
		String resourceTypeToPluginIdPath = "resourcetype2pluginid.json";
		String resourceTypeToProfileUrlPath = "resourcetype2profileurl.json";
		String profileUrlToPluginIdPath = "profileurl2pluginid.json";

		PluginMappingLoader pluginMappingLoader = new PluginMappingLoader(resourceTypeToPluginIdPath, resourceTypeToProfileUrlPath, profileUrlToPluginIdPath, new ObjectMapper());
		pluginMappingLoader.loadData();

		PluginMappingResolver pluginMappingResolver = new PluginMappingResolver(pluginMappingLoader);
		PluginLoader pluginLoader = new PluginLoader("plugins", true);
		pluginLoader.init();
		fhirValidationHandler = new FhirValidationHandler(pluginMappingResolver, pluginLoader, new FhirValidationBundleHandler());

		PluginLoader pluginLoaderDisabled = new PluginLoader("plugins", false);
		pluginLoaderDisabled.init();
		fhirValidationHandlerDisabledPlugins = new FhirValidationHandler(pluginMappingResolver, pluginLoaderDisabled, new FhirValidationBundleHandler());

		parser = FhirContext.forR4().newJsonParser();
	}

	@SneakyThrows
	@ParameterizedTest
	@ValueSource(strings = {
		"fhir-examples/valid/valid-resource.json",
		"fhir-examples/valid/valid-resource-no-meta-profile.json",
		"unknown-isik3-resourcetype.json",
		"unknown-resourcetype.json"
	})
	void shouldValidateValidJsonResource(String input) {
		String body = loadResourceAsString(input);
		IBaseResource resource = parser.parseResource(body);
		ValidationResult result = fhirValidationHandler.validateResource(resource, body);

		assertThat(result.isValid()).isTrue();
	}

	@Test
	@SneakyThrows
	void shouldValidateInvalidJsonResource_MetaProfile() {
		String body = loadResourceAsString("fhir-examples/invalid/invalid-resource.json");
		IBaseResource resource = parser.parseResource(body);
		ValidationResult result = fhirValidationHandler.validateResource(resource, body);

		assertThat(result.isValid()).isFalse();
	}

	@Test
	@SneakyThrows
	void shouldValidateInvalidJsonResource_ResourceType() {
		String body = loadResourceAsString("fhir-examples/invalid/invalid-resource-no-meta-profile.json");
		IBaseResource resource = parser.parseResource(body);
		ValidationResult result = fhirValidationHandler.validateResource(resource, body);

		assertThat(result.isValid()).isFalse();
	}

	@Test
	@SneakyThrows
	void shouldValidateResourceWithEmptyMetaProfile() {
		Patient patient = new Patient();
		patient.setId("test-patient");
		patient.getMeta().setProfile(Collections.emptyList());

		String body = parser.encodeResourceToString(patient);
		ValidationResult result = fhirValidationHandler.validateResource(patient, body);

		assertThat(result).isNotNull();
	}

	@Test
	@SneakyThrows
	void shouldValidateResourceWithMultipleProfiles() {
		Patient patient = new Patient();
		patient.setId("test-patient");
		patient.getMeta().addProfile("https://gematik.de/fhir/isik/v3/Basismodul/StructureDefinition/ISiKPatient");
		patient.getMeta().addProfile("http://hl7.org/fhir/StructureDefinition/Patient");

		String body = parser.encodeResourceToString(patient);
		IBaseResource resource = parser.parseResource(body);
		ValidationResult result = fhirValidationHandler.validateResource(resource, body);

		assertThat(result).isNotNull();
	}

	@Test
	@SneakyThrows
	void shouldValidateResourceWhenPluginsDisabled() {
		Patient patient = new Patient();
		patient.setId("test-patient");

		String body = parser.encodeResourceToString(patient);
		ValidationResult result = fhirValidationHandlerDisabledPlugins.validateResource(patient, body);

		assertThat(result).isNotNull();
	}

	@Test
	@SneakyThrows
	void shouldValidateBundleResourceWithoutMetaProfile() {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.COLLECTION);

		String body = parser.encodeResourceToString(bundle);
		ValidationResult result = fhirValidationHandler.validateResource(bundle, body);

		assertThat(result).isNotNull();
	}

	@Test
	@SneakyThrows
	void shouldValidateResourceWithNullResourceType() {
		Patient patient = new Patient();

		String body = parser.encodeResourceToString(patient);
		ValidationResult result = fhirValidationHandler.validateResource(patient, body);

		assertThat(result).isNotNull();
	}

	@Test
	@SneakyThrows
	void shouldCacheValidationModules() {
		String body = loadResourceAsString("fhir-examples/valid/valid-resource.json");
		IBaseResource resource = parser.parseResource(body);

		ValidationResult result1 = fhirValidationHandler.validateResource(resource, body);
		ValidationResult result2 = fhirValidationHandler.validateResource(resource, body);

		assertThat(result1.isValid()).isEqualTo(result2.isValid());
	}

	@Test
	@SneakyThrows
	void shouldValidateResourceWithIsikV3Profile() {
		Patient patient = new Patient();
		patient.setId("test-patient");
		patient.getMeta().addProfile("https://gematik.de/fhir/isik/v3/Basismodul/StructureDefinition/ISiKPatient");

		String body = parser.encodeResourceToString(patient);
		IBaseResource resource = parser.parseResource(body);
		ValidationResult result = fhirValidationHandler.validateResource(resource, body);

		assertThat(result).isNotNull();
	}
}
