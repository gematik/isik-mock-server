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
import de.gematik.isik.mockserver.refv.PluginLoader;
import de.gematik.isik.mockserver.refv.PluginMappingResolver;
import de.gematik.refv.Plugin;
import de.gematik.refv.SupportedValidationModule;
import de.gematik.refv.ValidationModuleFactory;
import de.gematik.refv.commons.exceptions.ValidationModuleInitializationException;
import de.gematik.refv.commons.validation.ValidationModule;
import de.gematik.refv.commons.validation.ValidationOptions;
import de.gematik.refv.commons.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.*;

import static de.gematik.isik.mockserver.interceptor.FhirValidationUtils.getValidationResult;

@Component
@RequiredArgsConstructor
@Slf4j
public class FhirValidationHandler {

	private final PluginMappingResolver pluginMappingResolver;
	private final PluginLoader pluginLoader;
	private final FhirValidationBundleHandler fhirValidationBundleHandler;

	public ValidationResult validateResource(IBaseResource resource, String body)
			throws ValidationModuleInitializationException {
		if (!resource.getMeta().getProfile().isEmpty()) {
			log.info("Validating resource using meta.profile...");
			return validateResourceWithProfile(resource, body);
		} else {
			log.info("Validating resource using ResourceType...");
			return validateResourceWithResourceType(resource, body);
		}
	}

	private ValidationResult validateResourceWithProfile(IBaseResource resource, String body)
			throws ValidationModuleInitializationException {
		Optional<String> isikProfile = FhirValidationHandlerHelper.findIsikProfile(resource);
		String profileToUse =
				isikProfile.orElse(resource.getMeta().getProfile().get(0).getValue());

		String pluginId = pluginMappingResolver.getPluginIdFromProfile(profileToUse);
		Plugin plugin = pluginLoader.getPlugin(pluginId);
		var validationModule = new ValidationModuleFactory().createValidationModuleFromPlugin(plugin);

		return validationModule.validateString(body);
	}

	private ValidationResult validateResourceWithResourceType(IBaseResource resource, String body)
			throws ValidationModuleInitializationException {
		String resourceType = resource.getIdElement().getResourceType();
		if (resourceType == null || resourceType.isEmpty()) {
			resourceType = FhirValidationHandlerHelper.getResourceType(body);
		}
		List<String> pluginIds = pluginMappingResolver.getPluginIdsFromResourceType(resourceType);
		List<String> profileUrls = pluginMappingResolver.getProfileUrlsFromResourceType(resourceType);
		List<Plugin> plugins;

		if (pluginIds.isEmpty() || !pluginLoader.isEnabled()) {
			return validateResourceWithCoreModule(resourceType, body);
		} else {
			plugins = new ArrayList<>(
					pluginIds.stream().map(pluginLoader::getPlugin).toList());
		}

		if (resourceType.equals("Bundle")) {
			return fhirValidationBundleHandler.validateBundleResourceWithPlugins(body, plugins, profileUrls);
		} else {
			List<ValidationOptions> validationOptionsList = profileUrls.stream()
					.map(profileUrl -> {
						ValidationOptions validationOptions = ValidationOptions.getDefaults();
						validationOptions.setProfiles(Collections.singletonList(profileUrl));
						return validationOptions;
					})
					.toList();

			return validateResourceWithPlugins(body, plugins, validationOptionsList);
		}
	}

	private ValidationResult validateResourceWithCoreModule(String resourceType, String body)
			throws ValidationModuleInitializationException {
		log.info("Validating resource using FHIR core validation module...");
		String profileUrl = "http://hl7.org/fhir/StructureDefinition/" + resourceType;
		ValidationOptions validationOptions = ValidationOptions.getDefaults();
		validationOptions.setProfiles(Collections.singletonList(profileUrl));
		var coreModule = new ValidationModuleFactory().createValidationModule(SupportedValidationModule.CORE);

		return coreModule.validateString(body, validationOptions);
	}

	private ValidationResult validateResourceWithPlugins(
			String body, List<Plugin> plugins, List<ValidationOptions> validationOptionsList)
			throws ValidationModuleInitializationException {
		List<ValidationModule> validationModules = new ArrayList<>();
		var allValidationMessages = new LinkedList<SingleValidationMessage>();

		for (Plugin plugin : plugins) {
			validationModules.add(new ValidationModuleFactory().createValidationModuleFromPlugin(plugin));
		}

		List<CompletableFuture<ValidationResult>> futures = validationModules.stream()
				.flatMap(validationModule -> validationOptionsList.stream()
						.map(validationOptions -> CompletableFuture.supplyAsync(() -> {
							var validationResult = validationModule.validateString(body, validationOptions);
							synchronized (allValidationMessages) { // Ensure thread-safe access to
								// shared list
								if (!validationResult.isValid()) {
									allValidationMessages.addAll(validationResult.getValidationMessages());
								}
							}
							return validationResult;
						})))
				.toList();

		return getValidationResult(allValidationMessages, futures);
	}
}
