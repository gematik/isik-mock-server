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

import ca.uhn.fhir.validation.SingleValidationMessage;
import de.gematik.refv.Plugin;
import de.gematik.refv.ValidationModuleFactory;
import de.gematik.refv.commons.exceptions.ValidationModuleInitializationException;
import de.gematik.refv.commons.validation.ValidationModule;
import de.gematik.refv.commons.validation.ValidationOptions;
import de.gematik.refv.commons.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static de.gematik.isik.mockserver.interceptor.FhirValidationUtils.getValidationResult;

@Component
public class FhirValidationBundleHandler {

	/*
	Incoming Bundle resources are handled separately because such incoming Bundle resources can be from three different fhir packages: isik3-medikation, isik3-basismodul and isik3-dokumentenaustausch
	Therefore we can not use the normal way of validating incoming resources with just a single isik3 Plugin. We need to validate against all three possibilities!
	 */

	public ValidationResult validateBundleResourceWithPlugins(
			String body, List<Plugin> plugins, List<String> profileUrls)
			throws ValidationModuleInitializationException {
		Map<String, ValidationOptions> validationOptionsMap =
				getValidationOptionsMapForBundleValidation(plugins, profileUrls);
		List<ValidationModule> validationModules = new ArrayList<>();
		var allValidationMessages = new LinkedList<SingleValidationMessage>();

		for (Plugin plugin : plugins) {
			validationModules.add(new ValidationModuleFactory().createValidationModuleFromPlugin(plugin));
		}

		List<CompletableFuture<ValidationResult>> futures = validationModules.stream()
				.map(validationModule -> CompletableFuture.supplyAsync(() -> {
					ValidationOptions validationOptions = validationOptionsMap.get(validationModule.getId());
					var validationResult = validationModule.validateString(body, validationOptions);
					synchronized (allValidationMessages) { // Ensure thread-safe access to shared list
						if (!validationResult.isValid()) {
							allValidationMessages.addAll(validationResult.getValidationMessages());
						}
					}
					return validationResult;
				}))
				.toList();

		return getValidationResult(allValidationMessages, futures);
	}

	private Map<String, ValidationOptions> getValidationOptionsMapForBundleValidation(
			List<Plugin> plugins, List<String> profileUrls) {
		Map<String, ValidationOptions> validationOptionsMap = new HashMap<>();

		for (Plugin plugin : plugins) {
			String pluginId = plugin.getId();
			ValidationOptions validationOptions = createValidationOptionsForPlugin(pluginId, profileUrls);
			if (validationOptions != null) {
				validationOptionsMap.put(pluginId, validationOptions);
			}
		}

		return validationOptionsMap;
	}

	private ValidationOptions createValidationOptionsForPlugin(String pluginId, List<String> profileUrls) {
		String keyword;

		switch (pluginId) {
			case "isik3-medikation":
				keyword = "Medikation";
				break;
			case "isik3-basismodul":
				keyword = "Basismodul";
				break;
			case "isik3-dokumentenaustausch":
				keyword = "Dokumentenaustausch";
				break;
			default:
				return null; // No ValidationOptions for unhandled plugins
		}

		String finalKeyword = keyword;
		List<String> filteredUrls = profileUrls.stream()
				.filter(profileUrl -> profileUrl.contains(finalKeyword))
				.toList();

		ValidationOptions validationOptions = ValidationOptions.getDefaults();
		validationOptions.setProfiles(filteredUrls);
		return validationOptions;
	}
}
