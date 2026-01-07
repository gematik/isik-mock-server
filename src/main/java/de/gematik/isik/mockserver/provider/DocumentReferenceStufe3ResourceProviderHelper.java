package de.gematik.isik.mockserver.provider;

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

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DocumentReferenceStufe3ResourceProviderHelper {

	@Autowired
	private IFhirResourceDao<Binary> binaryDao;

	@Autowired
	private IFhirResourceDao<Patient> patientDao;

	@Autowired
	private IFhirResourceDao<Encounter> encounterDao;

	@Autowired
	private KdlCodeMapper kdlCodeMapper;

	public static final String KDL_TYPE_CODE_SYSTEM = "http://dvmd.de/fhir/CodeSystem/kdl";
	public static final String XDS_TYPE_CODE_SYSTEM = "http://ihe-d.de/CodeSystems/IHEXDStypeCode";
	public static final String XDS_CLASS_CODE_SYSTEM = "http://ihe-d.de/CodeSystems/IHEXDSclassCode";
	private static final String MESSAGE_IS_UNKNOWN = " is unknown";
	private static final String DOCUMENT_REFERENCE_TYPE = "DocumentReference.type";

	public void validateBase64Data(byte[] base64data, OperationOutcome outcome) {
		if (base64data == null || base64data.length == 0) {
			log.info("Invalid request body: No base64 data in the attachment element found");
			OperationOutcomeUtils.addIssue(
					outcome,
					"DocumentReference.content.attachment[0]",
					"No base64 data in the attachment element found");
		}
	}

	public void validateEncounter(
			DocumentReference theResource, OperationOutcome outcome, RequestDetails requestDetails) {
		Reference encounterReference = theResource.getContext().getEncounter().get(0);
		if (!encounterExists(encounterReference, requestDetails)) {
			log.info("Invalid request body: Encounter " + encounterReference.getReference() + MESSAGE_IS_UNKNOWN);
			OperationOutcomeUtils.addIssue(
					outcome,
					"DocumentReference.context.encounter",
					String.format("Encounter %s%s", encounterReference.getReference(), MESSAGE_IS_UNKNOWN));
		}
	}

	public void validatePatient(
			DocumentReference theResource, OperationOutcome outcome, RequestDetails requestDetails) {
		if (!patientExists(theResource.getSubject(), requestDetails)) {
			log.info("Invalid request body: Patient " + theResource.getSubject().getReference() + MESSAGE_IS_UNKNOWN);
			OperationOutcomeUtils.addIssue(
					outcome,
					"DocumentReference.subject",
					String.format("Patient %s%s", theResource.getSubject().getReference(), MESSAGE_IS_UNKNOWN));
		}
	}

	private boolean encounterExists(Reference encounterReference, RequestDetails requestDetails) {
		try {
			encounterDao.read(new IdType(encounterReference.getReference()), requestDetails);
			return true;
		} catch (ResourceNotFoundException e) {
			return false;
		}
	}

	private boolean patientExists(Reference subject, RequestDetails requestDetails) {
		try {
			patientDao.read(new IdType(subject.getReference()), requestDetails);
			return true;
		} catch (ResourceNotFoundException e) {
			return false;
		}
	}

	public void mapKdlCodeToXdsClass(Coding kdlTypeCode, List<CodeableConcept> category, OperationOutcome outcome) {
		ConceptMap conceptMap = kdlCodeMapper.getClassCodeConceptMap();
		Coding targetCoding = kdlCodeMapper.findTargetCoding(
				conceptMap, kdlTypeCode.getCode(), KDL_TYPE_CODE_SYSTEM, XDS_CLASS_CODE_SYSTEM);
		if (targetCoding != null) {
			category.add(new CodeableConcept(targetCoding));
		} else {
			log.info("No mapping found for KDL code: " + kdlTypeCode.getCode() + " to " + XDS_CLASS_CODE_SYSTEM);
			OperationOutcomeUtils.addIssue(
					outcome,
					DOCUMENT_REFERENCE_TYPE,
					String.format(
							"No mapping found for KDL code: %s to %s", kdlTypeCode.getCode(), XDS_CLASS_CODE_SYSTEM));
		}
	}

	public void mapKdlCodeToXdsType(Coding kdlTypeCode, List<Coding> coding, OperationOutcome outcome) {
		ConceptMap conceptMap = kdlCodeMapper.getTypeCodeConceptMap();
		Coding targetCoding = kdlCodeMapper.findTargetCoding(
				conceptMap, kdlTypeCode.getCode(), KDL_TYPE_CODE_SYSTEM, XDS_TYPE_CODE_SYSTEM);
		if (targetCoding != null) {
			coding.add(targetCoding);
		} else {
			log.info("No mapping found for KDL code: " + kdlTypeCode.getCode() + " to " + XDS_TYPE_CODE_SYSTEM);
			OperationOutcomeUtils.addIssue(
					outcome,
					DOCUMENT_REFERENCE_TYPE,
					String.format(
							"No mapping found for KDL code: %s to %s", kdlTypeCode.getCode(), XDS_TYPE_CODE_SYSTEM));
		}
	}

	@NotNull
	public Coding getKDLTypeCode(DocumentReference resource, OperationOutcome outcome) {
		var kdlCode = resource.getType().getCoding().stream()
				.filter(c -> c.getSystem().equals(KDL_TYPE_CODE_SYSTEM))
				.findFirst();
		if (kdlCode.isEmpty()) {
			log.info("No KDL code in type element found");
			OperationOutcomeUtils.addIssue(outcome, DOCUMENT_REFERENCE_TYPE, "No KDL code in type element found");
			return new Coding();
		}
		return kdlCode.get();
	}

	public String createBinaryResourceAndGetUrl(byte[] base64data, RequestDetails requestDetails) {
		Binary newBinary = new Binary();
		newBinary.setData(base64data);
		newBinary.setContentType("application/pdf");
		var result = binaryDao.create(newBinary, requestDetails);
		return "/Binary/" + result.getId().getIdPart();
	}
}
