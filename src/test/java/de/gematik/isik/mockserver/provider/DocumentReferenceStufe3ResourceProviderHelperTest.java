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
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DocumentReferenceStufe3ResourceProviderHelperTest {

	@InjectMocks
	private DocumentReferenceStufe3ResourceProviderHelper helper;

	@Mock
	private IFhirResourceDao<Binary> binaryDao;

	@Mock
	private IFhirResourceDao<Patient> patientDao;

	@Mock
	private IFhirResourceDao<Encounter> encounterDao;

	@Mock
	private KdlCodeMapper kdlCodeMapper;

	@Mock
	private RequestDetails requestDetails;

	private static final String KDL_TYPE_CODE_SYSTEM = "http://dvmd.de/fhir/CodeSystem/kdl";

	private AutoCloseable mocks;

	@BeforeEach
	public void setup() {
		mocks = MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	@SneakyThrows
	void tearDown() {
		if (mocks != null) {
			mocks.close();
		}
	}

	@Test
	void testValidateBase64Data_ValidData() {
		OperationOutcome outcome = new OperationOutcome();
		helper.validateBase64Data(new byte[]{1, 2, 3}, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
		assertThat(outcome.getIssue()).isEmpty();
	}

	@Test
	void testValidateBase64Data_InvalidData() {
		OperationOutcome outcome = new OperationOutcome();
		helper.validateBase64Data(new byte[]{}, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
				.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
				.anyMatch(d -> d.contains("No base64 data in the attachment element found"));
	}

	@Test
	void testValidateEncounter_EncounterExists() {
		OperationOutcome outcome = new OperationOutcome();
		DocumentReference docRef = new DocumentReference();
		DocumentReference.DocumentReferenceContextComponent context = new DocumentReference.DocumentReferenceContextComponent();
		Reference encounterReference = new Reference("Encounter/123");
		List<Reference> encounters = new ArrayList<>();
		encounters.add(encounterReference);
		context.setEncounter(encounters);
		docRef.setContext(context);

		Encounter encounter = new Encounter();
		when(encounterDao.read(any(IdType.class), eq(requestDetails))).thenReturn(encounter);

		helper.validateEncounter(docRef, outcome, requestDetails);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
		assertThat(outcome.getIssue()).isEmpty();
	}

	@Test
	void testValidateEncounter_EncounterDoesNotExist() {
		OperationOutcome outcome = new OperationOutcome();
		DocumentReference docRef = new DocumentReference();
		DocumentReference.DocumentReferenceContextComponent context = new DocumentReference.DocumentReferenceContextComponent();
		Reference encounterReference = new Reference("Encounter/123");
		List<Reference> encounters = new ArrayList<>();
		encounters.add(encounterReference);
		context.setEncounter(encounters);
		docRef.setContext(context);

		when(encounterDao.read(any(IdType.class), eq(requestDetails)))
				.thenThrow(new ResourceNotFoundException("Not found"));

		helper.validateEncounter(docRef, outcome, requestDetails);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
				.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
				.anyMatch(d -> d.contains("Encounter Encounter/123 is unknown"));
	}

	@Test
	void testValidatePatient_PatientExists() {
		OperationOutcome outcome = new OperationOutcome();
		DocumentReference docRef = new DocumentReference();
		Reference patientReference = new Reference("Patient/456");
		docRef.setSubject(patientReference);

		Patient patient = new Patient();
		when(patientDao.read(any(IdType.class), eq(requestDetails))).thenReturn(patient);

		helper.validatePatient(docRef, outcome, requestDetails);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
		assertThat(outcome.getIssue()).isEmpty();
	}

	@Test
	void testValidatePatient_PatientDoesNotExist() {
		OperationOutcome outcome = new OperationOutcome();
		DocumentReference docRef = new DocumentReference();
		Reference patientReference = new Reference("Patient/456");
		docRef.setSubject(patientReference);

		when(patientDao.read(any(IdType.class), eq(requestDetails)))
				.thenThrow(new ResourceNotFoundException("Not found"));

		helper.validatePatient(docRef, outcome, requestDetails);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
				.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
				.anyMatch(d -> d.contains("Patient Patient/456 is unknown"));
	}

	@Test
	void testMapKdlCodeToXdsClass_MappingExists() {
		OperationOutcome outcome = new OperationOutcome();
		Coding kdlCoding = new Coding();
		kdlCoding.setCode("kdlCode1");
		kdlCoding.setSystem(KDL_TYPE_CODE_SYSTEM);

		ConceptMap conceptMap = new ConceptMap();
		Coding targetCoding = new Coding();
		targetCoding.setCode("xdsClass1");
		targetCoding.setSystem(DocumentReferenceStufe3ResourceProviderHelper.XDS_CLASS_CODE_SYSTEM);

		when(kdlCodeMapper.getClassCodeConceptMap()).thenReturn(conceptMap);
		when(kdlCodeMapper.findTargetCoding(conceptMap, "kdlCode1",
				KDL_TYPE_CODE_SYSTEM,
				DocumentReferenceStufe3ResourceProviderHelper.XDS_CLASS_CODE_SYSTEM))
				.thenReturn(targetCoding);

		List<CodeableConcept> category = new ArrayList<>();
		helper.mapKdlCodeToXdsClass(kdlCoding, category, outcome);

		assertThat(category).hasSize(1);
		CodeableConcept concept = category.get(0);
		assertThat(concept.getCoding()).extracting("code").containsExactly("xdsClass1");
		assertThat(outcome.getIssue()).isEmpty();
	}


	@Test
	void testMapKdlCodeToXdsClass_NoMapping() {
		OperationOutcome outcome = new OperationOutcome();
		Coding kdlCoding = new Coding();
		kdlCoding.setCode("kdlCode1");
		kdlCoding.setSystem(KDL_TYPE_CODE_SYSTEM);

		ConceptMap conceptMap = new ConceptMap();
		when(kdlCodeMapper.getClassCodeConceptMap()).thenReturn(conceptMap);
		when(kdlCodeMapper.findTargetCoding(conceptMap, "kdlCode1",
				KDL_TYPE_CODE_SYSTEM,
				DocumentReferenceStufe3ResourceProviderHelper.XDS_CLASS_CODE_SYSTEM))
				.thenReturn(null);

		List<CodeableConcept> category = new ArrayList<>();
		helper.mapKdlCodeToXdsClass(kdlCoding, category, outcome);

		assertThat(category).isEmpty();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
				.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
				.anyMatch(d -> d.contains("No mapping found for KDL code: kdlCode1 to " +
						DocumentReferenceStufe3ResourceProviderHelper.XDS_CLASS_CODE_SYSTEM));
	}

	@Test
	void testMapKdlCodeToXdsType_MappingExists() {
		OperationOutcome outcome = new OperationOutcome();
		Coding kdlCoding = new Coding();
		kdlCoding.setCode("kdlCode2");
		kdlCoding.setSystem(KDL_TYPE_CODE_SYSTEM);

		ConceptMap conceptMap = new ConceptMap();
		Coding targetCoding = new Coding();
		targetCoding.setCode("xdsType1");
		targetCoding.setSystem(DocumentReferenceStufe3ResourceProviderHelper.XDS_TYPE_CODE_SYSTEM);

		when(kdlCodeMapper.getTypeCodeConceptMap()).thenReturn(conceptMap);
		when(kdlCodeMapper.findTargetCoding(conceptMap, "kdlCode2",
				KDL_TYPE_CODE_SYSTEM,
				DocumentReferenceStufe3ResourceProviderHelper.XDS_TYPE_CODE_SYSTEM))
				.thenReturn(targetCoding);

		List<Coding> codingList = new ArrayList<>();
		helper.mapKdlCodeToXdsType(kdlCoding, codingList, outcome);

		assertThat(codingList).hasSize(1);
		Coding resultCoding = codingList.get(0);
		assertThat(resultCoding.getCode()).isEqualTo("xdsType1");
		assertThat(resultCoding.getSystem()).isEqualTo(DocumentReferenceStufe3ResourceProviderHelper.XDS_TYPE_CODE_SYSTEM);
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
		assertThat(outcome.getIssue()).isEmpty();
	}

	@Test
	void testMapKdlCodeToXdsType_NoMapping() {
		OperationOutcome outcome = new OperationOutcome();
		Coding kdlCoding = new Coding();
		kdlCoding.setCode("kdlCode2");
		kdlCoding.setSystem(KDL_TYPE_CODE_SYSTEM);

		ConceptMap conceptMap = new ConceptMap();
		when(kdlCodeMapper.getTypeCodeConceptMap()).thenReturn(conceptMap);
		when(kdlCodeMapper.findTargetCoding(conceptMap, "kdlCode2",
				KDL_TYPE_CODE_SYSTEM,
				DocumentReferenceStufe3ResourceProviderHelper.XDS_TYPE_CODE_SYSTEM))
				.thenReturn(null);

		List<Coding> codingList = new ArrayList<>();
		helper.mapKdlCodeToXdsType(kdlCoding, codingList, outcome);

		assertThat(codingList).isEmpty();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
				.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
				.anyMatch(d -> d.contains("No mapping found for KDL code: kdlCode2 to " +
						DocumentReferenceStufe3ResourceProviderHelper.XDS_TYPE_CODE_SYSTEM));
	}

	@Test
	void testGetKDLTypeCode_Found() {
		OperationOutcome outcome = new OperationOutcome();
		DocumentReference docRef = new DocumentReference();
		CodeableConcept typeConcept = new CodeableConcept();
		Coding coding = new Coding();
		coding.setCode("kdlCode3");
		coding.setSystem(KDL_TYPE_CODE_SYSTEM);
		typeConcept.addCoding(coding);
		docRef.setType(typeConcept);

		Coding result = helper.getKDLTypeCode(docRef, outcome);
		assertThat(result.getCode()).isEqualTo("kdlCode3");
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
		assertThat(outcome.getIssue()).isEmpty();
	}

	@Test
	void testGetKDLTypeCode_NotFound() {
		OperationOutcome outcome = new OperationOutcome();
		DocumentReference docRef = new DocumentReference();
		CodeableConcept typeConcept = new CodeableConcept();
		Coding coding = new Coding();
		coding.setCode("otherCode");
		coding.setSystem("someOtherSystem");
		typeConcept.addCoding(coding);
		docRef.setType(typeConcept);

		Coding result = helper.getKDLTypeCode(docRef, outcome);
		assertThat(result.getCode()).isNull();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
				.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
				.anyMatch(d -> d.contains("No KDL code in type element found"));
	}

	@Test
	void testCreateBinaryResourceAndGetUrl_ReturnsUrl() {
		byte[] data = new byte[]{1, 2, 3};
		Binary createdBinary = new Binary();
		createdBinary.setId("Binary/123");

		DaoMethodOutcome outcome = new DaoMethodOutcome();
		outcome.setId(new IdType("Binary", "123"));
		outcome.setResource(createdBinary);

		when(binaryDao.create(any(Binary.class), eq(requestDetails))).thenReturn(outcome);

		String url = helper.createBinaryResourceAndGetUrl(data, requestDetails);
		assertThat(url).isEqualTo("/Binary/123");
	}
}
