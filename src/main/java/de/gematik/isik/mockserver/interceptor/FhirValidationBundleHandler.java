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
import de.gematik.refv.commons.exceptions.ValidationModuleInitializationException;
import de.gematik.refv.commons.validation.ValidationModule;
import de.gematik.refv.commons.validation.ValidationOptions;
import de.gematik.refv.commons.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static de.gematik.isik.mockserver.interceptor.FhirValidationHandlerHelper.ISIK_5_PLUGIN_ID;
import static de.gematik.isik.mockserver.interceptor.FhirValidationHandlerHelper.ISIK_LEGACY_PROFILE_VERSION;
import static de.gematik.isik.mockserver.interceptor.FhirValidationUtils.getValidationResult;

@Slf4j
@Component
public class FhirValidationBundleHandler {
	private final Map<String, ValidationModule> moduleCache = new ConcurrentHashMap<>();

	/*
	Incoming Bundle resources are handled separately because such incoming Bundle resources can be from three different fhir packages: isik3-medikation, isik3-basismodul and isik3-dokumentenaustausch
	Therefore we can not use the normal way of validating incoming resources with just a single isik3 Plugin. We need to validate against all three possibilities!
	 */

	public ValidationResult validateBundleResourceWithPlugins(
			String body, List<Plugin> plugins, List<String> profileUrls)
			throws ValidationModuleInitializationException {

		final var allValidationMessages = new LinkedList<SingleValidationMessage>();

		// Validate using ISiK5 first
		final var isik5Plugin = FhirValidationHandlerHelper.findPlugin(plugins, ISIK_5_PLUGIN_ID);
		final var isik5ValidationOptions = createBundleValidationOptionsForPlugin(ISIK_5_PLUGIN_ID, profileUrls);

		if (isik5Plugin.isPresent() && isik5ValidationOptions != null) {
			log.info("Validating resource using ISiK5 plugin first...");
			final var isik5ValidationModule = getOrCreateModule(isik5Plugin.get());
			final var validationResult =
					FhirValidationHandlerHelper.performValidation(body, isik5ValidationModule, isik5ValidationOptions);
			if (validationResult.isValid()) {
				return validationResult;
			}

			allValidationMessages.addAll(validationResult.getValidationMessages());
			log.warn("ISiK5 validation found issues, proceeding with legacy modules...");
		}

		// Validate using ISiK3 plugins, remove ISIK5 plugin from the list to avoid duplicate validation
		List<Plugin> fallbackPlugins = FhirValidationHandlerHelper.filterOutById(plugins, ISIK_5_PLUGIN_ID);
		var validationOptionsMap = getValidationOptionsMapForBundleValidation(fallbackPlugins, profileUrls);
		List<ValidationModule> validationModules = new ArrayList<>();
		for (Plugin plugin : fallbackPlugins) {
			validationModules.add(getOrCreateModule(plugin));
		}

		List<CompletableFuture<ValidationResult>> futures = validationModules.stream()
				.map(validationModule -> CompletableFuture.supplyAsync(() -> {
					log.info("Validating Bundle with module: {}", validationModule.getId());
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

	/**
	 * Get or create a ValidationModule for the given plugin, using a cache to avoid redundant
	 * initializations.
	 *
	 * @param plugin the plugin for which to get or create the ValidationModule
	 * @return the ValidationModule associated with the plugin
	 * @throws ValidationModuleInitializationException if the module cannot be initialized
	 */
	public ValidationModule getOrCreateModule(Plugin plugin) throws ValidationModuleInitializationException {
		if (moduleCache.containsKey(plugin.getId())) {
			return moduleCache.get(plugin.getId());
		}

		final var module = FhirValidationHandlerHelper.createFromPlugin(plugin);
		moduleCache.put(plugin.getId(), module);
		return module;
	}

	private Map<String, ValidationOptions> getValidationOptionsMapForBundleValidation(
			List<Plugin> plugins, List<String> profileUrls) {
		Map<String, ValidationOptions> validationOptionsMap = new HashMap<>();

		for (Plugin plugin : plugins) {
			String pluginId = plugin.getId();
			ValidationOptions validationOptions = createBundleValidationOptionsForPlugin(pluginId, profileUrls);
			if (validationOptions != null) {
				validationOptionsMap.put(pluginId, validationOptions);
			}
		}

		return validationOptionsMap;
	}

	/**
	 * Based on the given plugin ID, create appropriate ValidationOptions for Bundle validation.
	 *
	 * @param pluginId the plugin ID for which to create ValidationOptions
	 * @param profileUrls the list of profile URLs to filter from
	 * @return the created ValidationOptions, or null if the plugin ID is not handled/known
	 */
	private ValidationOptions createBundleValidationOptionsForPlugin(String pluginId, List<String> profileUrls) {
		String keyword;

		switch (pluginId) {
			case ISIK_5_PLUGIN_ID:
				keyword = ""; // No specific keyword filtering for isik5
				break;
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

		if (ISIK_5_PLUGIN_ID.equals(pluginId)) {
			// filter out legacy profiles
			List<String> filteredUrls = profileUrls.stream()
					.filter(profileUrl -> !profileUrl.contains(ISIK_LEGACY_PROFILE_VERSION))
					.toList();

			// For isik5, include all profiles
			ValidationOptions validationOptions = ValidationOptions.getDefaults();
			validationOptions.setProfiles(filteredUrls);
			return validationOptions;
		}

		String finalKeyword = keyword;
		List<String> filteredUrls = profileUrls.stream()
				.filter(profileUrl ->
						profileUrl.contains(ISIK_LEGACY_PROFILE_VERSION) && profileUrl.contains(finalKeyword))
				.toList();

		ValidationOptions validationOptions = ValidationOptions.getDefaults();
		validationOptions.setProfiles(filteredUrls);
		return validationOptions;
	}
}
