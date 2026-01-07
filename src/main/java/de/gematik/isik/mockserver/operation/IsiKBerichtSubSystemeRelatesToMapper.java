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
import org.hl7.fhir.instance.model.api.IBaseDatatype;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class IsiKBerichtSubSystemeRelatesToMapper {

	@Autowired
	private final DaoRegistry daoRegistry;

	public List<DocumentReference.DocumentReferenceRelatesToComponent> mapRelatesToComponents(
			Composition composition, RequestDetails requestDetails) {
		List<DocumentReference.DocumentReferenceRelatesToComponent> relatesToList = new ArrayList<>();

		for (Composition.CompositionRelatesToComponent compRelatesTo : composition.getRelatesTo()) {
			Optional<Identifier> optIdentifier = resolveValidIdentifier(compRelatesTo);
			if (optIdentifier.isEmpty()) {
				continue;
			}

			Identifier identifier = optIdentifier.get();
			String system = identifier.getSystem();
			String value = identifier.getValue();

			SearchParameterMap params = new SearchParameterMap();
			params.add("identifier", new TokenParam(system, value));
			IBundleProvider searchResult =
					daoRegistry.getResourceDao(DocumentReference.class).search(params, requestDetails);

			// Fetch up to 2 resources since we expect only 1
			List<DocumentReference> docRefs = searchResult.getResources(0, 2).stream()
					.map(DocumentReference.class::cast)
					.toList();

			if (docRefs.size() == 1) {
				DocumentReference.DocumentReferenceRelatesToComponent drRelatesTo =
						new DocumentReference.DocumentReferenceRelatesToComponent();
				drRelatesTo.setCode(DocumentReference.DocumentRelationshipType.fromCode(
						compRelatesTo.getCode().toCode()));
				drRelatesTo.setTarget(new Reference(docRefs.get(0)));
				relatesToList.add(drRelatesTo);
			} else if (docRefs.isEmpty()) {
				log.warn("No DocumentReference found for identifier: {}|{}", system, value);
			} else {
				log.warn("Multiple DocumentReferences found for identifier: {}|{}, expected one.", system, value);
			}
		}

		return relatesToList;
	}

	private Optional<Identifier> resolveValidIdentifier(Composition.CompositionRelatesToComponent compRelatesTo) {
		IBaseDatatype target = compRelatesTo.getTarget();
		if (target == null) {
			log.warn("Composition.relatesTo.target is null.");
			return Optional.empty();
		}
		Identifier identifier;
		if (target instanceof Reference ref) {
			identifier = ref.getIdentifier();
		} else if (target instanceof Identifier id) {
			identifier = id;
		} else {
			log.warn("Unexpected target type: {}", target.getClass().getSimpleName());
			return Optional.empty();
		}
		if (identifier == null || identifier.getSystem() == null || identifier.getValue() == null) {
			log.warn("Identifier in Composition.relatesTo.target is missing system or value.");
			return Optional.empty();
		}
		return Optional.of(identifier);
	}
}
