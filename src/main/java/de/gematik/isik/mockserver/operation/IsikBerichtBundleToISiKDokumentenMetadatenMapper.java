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

import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class IsikBerichtBundleToISiKDokumentenMetadatenMapper {

	@Autowired
	private final IsikBerichtSubSystemeTypeMapper typeMapper;

	@Autowired
	private final IsikBerichtSubSystemeSubjectMapper subjectMapper;

	@Autowired
	private final IsiKBerichtSubSystemeRelatesToMapper relatesToMapper;

	@Autowired
	private final IsiKBerichtSubSystemeEncounterMapper encounterMapper;

	@Autowired
	private final IsiKBerichtSubSystemeContentMapper contentMapper;

	public DocumentReferenceMetadataReturnObject mapCompositionToDocumentReference(
			Composition composition,
			EncodingEnum encoding,
			Identifier bundleIdentifier,
			String createdBundleId,
			RequestDetails requestDetails) {
		OperationOutcome operationOutcome = new OperationOutcome();
		DocumentReference documentReference = new DocumentReference();

		documentReference.setMasterIdentifier(bundleIdentifier);
		documentReference.setIdentifier(List.of(composition.getIdentifier()));
		documentReference.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
		documentReference.setDocStatus(DocumentReference.ReferredDocumentStatus.fromCode(
				composition.getStatus().toCode()));

		typeMapper.mapKdlAndXdsCodings(composition, documentReference, operationOutcome);

		Reference patientReference = subjectMapper.mapSubject(composition, operationOutcome, requestDetails);
		if (patientReference != null) {
			documentReference.setSubject(patientReference);
		}

		Reference authorRef = new Reference();
		String authorDisplay = composition.getAuthor().get(0).getDisplay();
		if (authorDisplay == null || authorDisplay.isEmpty()) {
			log.warn("No display value found for Composition.author");
		} else {
			authorRef.setDisplay(authorDisplay);
		}
		documentReference.setAuthor(List.of(authorRef));

		List<DocumentReference.DocumentReferenceRelatesToComponent> relatesToComponents =
				relatesToMapper.mapRelatesToComponents(composition, requestDetails);
		documentReference.setRelatesTo(relatesToComponents);

		documentReference.setDescription(composition.getTitle());
		DocumentReference.DocumentReferenceContentComponent contentComponent =
				contentMapper.mapContentComponent(composition, encoding, createdBundleId);
		documentReference.setContent(List.of(contentComponent));

		DocumentReference.DocumentReferenceContextComponent documentReferenceContextComponent =
				new DocumentReference.DocumentReferenceContextComponent();
		encounterMapper.mapEncounter(composition, documentReferenceContextComponent, requestDetails);
		documentReferenceContextComponent.setFacilityType(new CodeableConcept(
				new Coding("http://ihe-d.de/CodeSystems/PatientBezogenenGesundheitsversorgung", "KHS", "Krankenhaus")));
		documentReferenceContextComponent.setPracticeSetting(new CodeableConcept(
				new Coding("http://ihe-d.de/CodeSystems/NichtaerztlicheFachrichtungen", "VER", "Default")));
		documentReference.setContext(documentReferenceContextComponent);

		return !OperationOutcomeUtils.hasErrorIssue(operationOutcome)
				? new DocumentReferenceMetadataReturnObject(documentReference, true, null)
				: new DocumentReferenceMetadataReturnObject(null, false, operationOutcome);
	}
}
