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

import de.gematik.isik.mockserver.refv.PluginLoader;
import de.gematik.refv.Plugin;
import de.gematik.refv.commons.validation.ValidationResult;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.gematik.isik.mockserver.helper.ResourceLoadingHelper.loadResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;

class FhirValidationBundleHandlerTest {

	private FhirValidationBundleHandler fhirValidationBundleHandler;

	List<String> profileUrls = List.of(
			"https://gematik.de/fhir/isik/v3/Medikation/StructureDefinition/ISiKMedikationTransaction",
			"https://gematik.de/fhir/isik/v3/Medikation/StructureDefinition/ISiKMedikationTransactionResponse",
			"https://gematik.de/fhir/isik/v3/Dokumentenaustausch/StructureDefinition/ISiKDokumentenSuchergebnisse",
			"https://gematik.de/fhir/isik/v3/Basismodul/StructureDefinition/ISiKBerichtBundle");

	List<Plugin> plugins = new ArrayList<>();

	@BeforeEach
	void setUp() {
		fhirValidationBundleHandler = new FhirValidationBundleHandler();

		PluginLoader pluginLoader = new PluginLoader("plugins", true);
		pluginLoader.init();

		Plugin basismodul = pluginLoader.getPlugin("isik3-basismodul");
		Plugin medikation = pluginLoader.getPlugin("isik3-medikation");
		Plugin dokumentenaustausch = pluginLoader.getPlugin("isik3-dokumentenaustausch");

		plugins.add(basismodul);
		plugins.add(medikation);
		plugins.add(dokumentenaustausch);
	}

	@SneakyThrows
	@Test
	void shouldValidateValidBundle() {
		String body = loadResourceAsString("fhir-examples/valid/valid-bundle.json");
		ValidationResult result = fhirValidationBundleHandler.validateBundleResourceWithPlugins(body, plugins, profileUrls);

		assertThat(result.isValid()).isTrue();
	}

	@SneakyThrows
	@Test
	void shouldValidateInvalidBundle() {
		String body = loadResourceAsString("fhir-examples/invalid/invalid-bundle.json");
		ValidationResult result = fhirValidationBundleHandler.validateBundleResourceWithPlugins(body, plugins, profileUrls);

		assertThat(result.isValid()).isFalse();
	}
}
