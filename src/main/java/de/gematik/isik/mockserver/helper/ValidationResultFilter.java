package de.gematik.isik.mockserver.helper;

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

import de.gematik.refv.commons.validation.ValidationResult;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ValidationResultFilter {

	public ValidationResult filter(ValidationResult result) {
		log.info("Filtering validation result...");
		result.getValidationMessages()
				.removeIf(message -> "Extension_EXT_Version_Invalid".equals(message.getMessageId())
						&& message.getMessage()
								.contains(
										"http://hl7.org/fhir/5.0/StructureDefinition/extension-Appointment.replaces"));

		return new ValidationResult(result.getValidationMessages());
	}
}
