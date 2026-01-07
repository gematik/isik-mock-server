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
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FhirValidationHandlerHelperTest {

	@Test
	void testGetResourceType() {
		String body = "{\"resourceType\": \"Appointment\"}";

		String resourceType = FhirValidationHandlerHelper.getResourceType(body);
		assertThat(resourceType).isEqualTo("Appointment");
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
}
