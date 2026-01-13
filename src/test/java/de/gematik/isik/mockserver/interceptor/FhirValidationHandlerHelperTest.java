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
import de.gematik.refv.Plugin;
import de.gematik.refv.SupportedValidationModule;
import de.gematik.refv.commons.validation.ValidationOptions;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FhirValidationHandlerHelperTest {

	@Test
	void testGetResourceType() {
		String body = "{\"resourceType\": \"Appointment\"}";

		String resourceType = FhirValidationHandlerHelper.getResourceType(body);
		assertThat(resourceType).isEqualTo("Appointment");
	}

	@Test
	void testGetResourceType_notFound() {
		String body = "{\"someField\": \"value\"}";

		String resourceType = FhirValidationHandlerHelper.getResourceType(body);
		assertThat(resourceType).isNull();
	}

	@Test
	void testFindIsikProfile() {
		String body = """
                {
                  "resourceType": "Appointment",
                  "meta": {
                    "profile": [
                      "hhtp://some-other-profile",
                      "https://gematik.de/fhir/isik/v3/Terminplanung/StructureDefinition/ISiKTermin"
                    ]
                  }
                }""";
		IBaseResource resource = FhirContext.forR4().newJsonParser().parseResource(body);

		Optional<String> isikProfile = FhirValidationHandlerHelper.findIsikProfile(resource);

		assertThat(isikProfile)
			.isPresent()
			.contains("https://gematik.de/fhir/isik/v3/Terminplanung/StructureDefinition/ISiKTermin");
	}

	@Test
	void testFindIsikProfile_notFound() {
		String body = """
                {
                  "resourceType": "Appointment",
                  "meta": {
                    "profile": [
                      "http://some-other-profile"
                    ]
                  }
                }""";
		IBaseResource resource = FhirContext.forR4().newJsonParser().parseResource(body);

		Optional<String> isikProfile = FhirValidationHandlerHelper.findIsikProfile(resource);

		assertThat(isikProfile).isEmpty();
	}

	@Test
	void testFindPlugin() {
		Plugin plugin1 = mock(Plugin.class);
		Plugin plugin2 = mock(Plugin.class);
		when(plugin1.getId()).thenReturn("isik4");
		when(plugin2.getId()).thenReturn("isik5");

		List<Plugin> plugins = List.of(plugin1, plugin2);

		Optional<Plugin> result = FhirValidationHandlerHelper.findPlugin(plugins, "isik5");

		assertThat(result).isPresent().contains(plugin2);
	}

	@Test
	void testFindPlugin_notFound() {
		Plugin plugin1 = mock(Plugin.class);
		when(plugin1.getId()).thenReturn("isik4");

		List<Plugin> plugins = List.of(plugin1);

		Optional<Plugin> result = FhirValidationHandlerHelper.findPlugin(plugins, "isik5");

		assertThat(result).isEmpty();
	}

	@Test
	void testFilterOutById() {
		Plugin plugin1 = mock(Plugin.class);
		Plugin plugin2 = mock(Plugin.class);
		when(plugin1.getId()).thenReturn("isik4");
		when(plugin2.getId()).thenReturn("isik5");

		List<Plugin> plugins = List.of(plugin1, plugin2);

		List<Plugin> result = FhirValidationHandlerHelper.filterOutById(plugins, "isik5");

		assertThat(result).containsExactly(plugin1);
	}

	@Test
	void testFindByProfile() {
		ValidationOptions options1 = ValidationOptions.getDefaults();
		options1.setProfiles(List.of("https://gematik.de/fhir/isik/v3/profile"));
		ValidationOptions options2 = ValidationOptions.getDefaults();
		options2.setProfiles(List.of("https://gematik.de/fhir/isik/v4/profile"));

		List<ValidationOptions> optionsList = List.of(options1, options2);

		List<ValidationOptions> result = FhirValidationHandlerHelper.findByProfile(optionsList, "v3");

		assertThat(result).containsExactly(options1);
	}

	@Test
	void testFilterOutByProfile() {
		ValidationOptions options1 = ValidationOptions.getDefaults();
		options1.setProfiles(List.of("https://gematik.de/fhir/isik/v3/profile"));
		ValidationOptions options2 = ValidationOptions.getDefaults();
		options2.setProfiles(List.of("https://gematik.de/fhir/isik/v4/profile"));

		List<ValidationOptions> optionsList = List.of(options1, options2);

		List<ValidationOptions> result = FhirValidationHandlerHelper.filterOutByProfile(optionsList, "v3");

		assertThat(result).containsExactly(options2);
	}

	@Test
	void testCreateFromModule() {
		assertThatNoException().isThrownBy(() ->
			FhirValidationHandlerHelper.createFromModule(SupportedValidationModule.CORE)
		);
	}
}
