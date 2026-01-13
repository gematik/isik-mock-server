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

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import de.gematik.refv.Plugin;
import de.gematik.refv.SupportedValidationModule;
import de.gematik.refv.ValidationModuleFactory;
import de.gematik.refv.commons.exceptions.ValidationModuleInitializationException;
import de.gematik.refv.commons.validation.ValidationModule;
import de.gematik.refv.commons.validation.ValidationOptions;
import de.gematik.refv.commons.validation.ValidationResult;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class FhirValidationHandlerHelper {
	private static final ValidationModuleFactory VALIDATION_MODULE_FACTORY = new ValidationModuleFactory();
	public static final String ISIK_LEGACY_PROFILE_VERSION = "v3";
	public static final String ISIK_5_PLUGIN_ID = "isik5";

	public String getResourceType(String body) {
		Matcher m = Pattern.compile("\"resourceType\"\\s*:\\s*\"(.*?)\"").matcher(body);
		return m.find() ? m.group(1) : null;
	}

	public Optional<String> findIsikProfile(IBaseResource resource) {
		return resource.getMeta().getProfile().stream()
				.map(IPrimitiveType::getValue)
				.filter(profile -> profile.startsWith("https://gematik.de/fhir/isik"))
				.findFirst();
	}

	/**
	 * Finds a Plugin by its pluginId from the given list of plugins.
	 *
	 * @param plugins list of plugins to search
	 * @param pluginId the pluginId to find
	 * @return an Optional containing the found Plugin, or empty if not found
	 */
	public Optional<Plugin> findPlugin(List<Plugin> plugins, String pluginId) {
		return plugins.stream()
				.filter(plugin -> pluginId.equalsIgnoreCase(plugin.getId()))
				.findFirst();
	}

	/**
	 * Gets a list of plugins excluding the ones with the given pluginId.
	 *
	 * @param plugins list of plugins to filter
	 * @param pluginId pluginId to filter out
	 * @return list of plugins excluding the ones with the given pluginId
	 */
	public List<Plugin> filterOutById(List<Plugin> plugins, String pluginId) {
		return plugins.stream()
				.filter(plugin -> !pluginId.contains(plugin.getId()))
				.toList();
	}

	/**
	 * Finds ValidationOptions by evaluating if the Profile URL contains the given search parameter.
	 *
	 * @param validationOptions the list of ValidationOptions to search
	 * @param searchParameter the search parameter to look for in the Profile URL
	 * @return a list of ValidationOptions that match the search criteria
	 */
	public List<ValidationOptions> findByProfile(List<ValidationOptions> validationOptions, String searchParameter) {
		return validationOptions.stream()
				.filter(options -> options.getProfiles().getFirst().contains(searchParameter))
				.toList();
	}

	/**
	 * Retrieves ValidationOptions by evaluating if the Profile URL does not contain the given search
	 * parameter.
	 *
	 * @param validationOptions the list of ValidationOptions to filter
	 * @param searchParameter the search parameter to look for in the Profile URL
	 * @return a list of ValidationOptions that do not match the search criteria
	 */
	public List<ValidationOptions> filterOutByProfile(
			List<ValidationOptions> validationOptions, String searchParameter) {
		return validationOptions.stream()
				.filter(options -> !options.getProfiles().getFirst().contains(searchParameter))
				.toList();
	}

	/**
	 * Creates a ValidationModule from the given Plugin.
	 *
	 * @param plugin the Plugin
	 * @return the created ValidationModule
	 * @throws ValidationModuleInitializationException if the module cannot be created
	 */
	public ValidationModule createFromPlugin(Plugin plugin) throws ValidationModuleInitializationException {
		return VALIDATION_MODULE_FACTORY.createValidationModuleFromPlugin(plugin);
	}

	/**
	 * Creates a ValidationModule from the given SupportedValidationModule.
	 *
	 * @param module the SupportedValidationModule
	 * @return the created ValidationModule
	 * @throws ValidationModuleInitializationException if the module cannot be created
	 */
	public ValidationModule createFromModule(SupportedValidationModule module)
			throws ValidationModuleInitializationException {
		return VALIDATION_MODULE_FACTORY.createValidationModule(module);
	}

	/**
	 * Validates a Request Body using the provided validation module and validation options.
	 *
	 * @param body the FHIR resource as a string
	 * @param validationModule the ValidationModule to use for validation
	 * @param validationOptions the ValidationOptions to apply during validation
	 * @return the ValidationResult of the validation process
	 */
	public ValidationResult performValidation(
			String body, ValidationModule validationModule, ValidationOptions validationOptions) {
		try {
			return validationModule.validateString(body, validationOptions);
		} catch (Exception e) {
			final var validationMessage = new SingleValidationMessage();
			validationMessage.setMessage(String.format(
					"Failed to initialize validation module for %s: %s", validationModule.getId(), e.getMessage()));
			validationMessage.setSeverity(ResultSeverityEnum.ERROR);
			return new ValidationResult(List.of(validationMessage));
		}
	}
}
