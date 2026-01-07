package de.gematik.isik.mockserver.operation;

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

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class IsiKBerichtSubSystemeEncounterMapper {

	@Autowired
	private final DaoRegistry daoRegistry;

	public void mapEncounter(
			Composition composition,
			DocumentReference.DocumentReferenceContextComponent contextComponent,
			RequestDetails requestDetails) {
		if (!composition.hasEncounter()) {
			log.warn("No Encounter referenced in Composition.encounter");
			return;
		}

		Encounter encounterInBundle = (Encounter) composition.getEncounter().getResource();
		if (encounterInBundle == null) {
			log.warn("Encounter referenced in Composition.encounter not found in the Bundle");
			return;
		}

		Optional<Identifier> identifierOptional = encounterInBundle.getIdentifier().stream()
				.filter(id -> id.getType().getCoding().stream()
						.anyMatch(coding -> "VN".equals(coding.getCode())
								&& "http://terminology.hl7.org/CodeSystem/v2-0203".equals(coding.getSystem())))
				.findFirst();

		if (identifierOptional.isEmpty()) {
			log.warn("No VN identifier found in the Encounter referenced by Composition.encounter");
			return;
		}

		Identifier identifier = identifierOptional.get();
		String system = identifier.getSystem();
		String value = identifier.getValue();

		SearchParameterMap params = new SearchParameterMap();
		params.add("identifier", new TokenParam(system, value));
		IBundleProvider searchResult =
				daoRegistry.getResourceDao(Encounter.class).search(params, requestDetails);
		List<Encounter> encounters = searchResult.getResources(0, 2).stream()
				.map(Encounter.class::cast)
				.toList();

		if (encounters.size() == 1) {
			Encounter serverEncounter = encounters.get(0);
			contextComponent.setEncounter(List.of(new Reference(serverEncounter)));
			return;
		}

		if (encounters.isEmpty()) {
			log.warn("No Encounter found on server with identifier: {}|{}", system, value);
			return;
		}

		log.warn("Multiple Encounters found on server with identifier: {}|{}, expected one.", system, value);
	}
}
