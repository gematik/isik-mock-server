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

import de.gematik.isik.mockserver.provider.KdlCodeMapper;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static de.gematik.isik.mockserver.provider.DocumentReferenceStufe3ResourceProviderHelper.KDL_TYPE_CODE_SYSTEM;
import static de.gematik.isik.mockserver.provider.DocumentReferenceStufe3ResourceProviderHelper.XDS_CLASS_CODE_SYSTEM;
import static de.gematik.isik.mockserver.provider.DocumentReferenceStufe3ResourceProviderHelper.XDS_TYPE_CODE_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class IsikBerichtSubSystemeTypeMapperTest {

	private IsikBerichtSubSystemeTypeMapper mapper;

	@Mock private KdlCodeMapper kdlCodeMapper;

	private AutoCloseable mocks;

	@BeforeEach
	void setup() {
		mocks = MockitoAnnotations.openMocks(this);
		mapper = new IsikBerichtSubSystemeTypeMapper(kdlCodeMapper);
	}

	@AfterEach
	@SneakyThrows
	void tearDown() {
		if (mocks != null) {
			mocks.close();
		}
	}

	@Test
	void testMapKdlAndXdsCodings_MissingKdlCoding() {
		Composition composition = new Composition();
		CodeableConcept compType = new CodeableConcept();
		compType.addCoding(new Coding().setSystem("http://not.kdl").setCode("dummy"));
		composition.setType(compType);
		composition.setCategory(Collections.emptyList());

		DocumentReference documentReference = new DocumentReference();
		OperationOutcome outcome = new OperationOutcome();

		mapper.mapKdlAndXdsCodings(composition, documentReference, outcome);

		assertThat(outcome.getIssue()).isNotEmpty();
		assertThat(outcome.getIssue().get(0).getDiagnostics())
			.contains("Missing required KDL coding in Composition.type.coding");
		assertThat(documentReference.getType().getCoding()).isEmpty();
	}

	@Test
	void testMapKdlAndXdsCodings_MappingForXdsTypeAndXdsClassExists() {
		Composition composition = new Composition();
		CodeableConcept compType = new CodeableConcept();
		Coding kdlCoding = new Coding().setSystem(KDL_TYPE_CODE_SYSTEM).setCode("kdl1");
		compType.addCoding(kdlCoding);
		composition.setType(compType);
		composition.setCategory(Collections.emptyList());

		DocumentReference documentReference = new DocumentReference();
		OperationOutcome outcome = new OperationOutcome();

		ConceptMap typeConceptMap = new ConceptMap();
		when(kdlCodeMapper.getTypeCodeConceptMap()).thenReturn(typeConceptMap);
		Coding xdsTypeCoding = new Coding().setSystem(XDS_TYPE_CODE_SYSTEM).setCode("xdsType1");
		when(kdlCodeMapper.findTargetCoding(typeConceptMap, "kdl1", KDL_TYPE_CODE_SYSTEM, XDS_TYPE_CODE_SYSTEM))
			.thenReturn(xdsTypeCoding);

		ConceptMap classConceptMap = new ConceptMap();
		when(kdlCodeMapper.getClassCodeConceptMap()).thenReturn(classConceptMap);
		Coding xdsClassCoding = new Coding().setSystem(XDS_CLASS_CODE_SYSTEM).setCode("xdsClass1");
		when(kdlCodeMapper.findTargetCoding(classConceptMap, "kdl1", KDL_TYPE_CODE_SYSTEM, XDS_CLASS_CODE_SYSTEM))
			.thenReturn(xdsClassCoding);

		mapper.mapKdlAndXdsCodings(composition, documentReference, outcome);

		CodeableConcept docType = documentReference.getType();
		assertThat(docType).isNotNull();
		assertThat(docType.getCoding()).hasSize(2);
		assertThat(docType.getCoding().get(0).getSystem()).isEqualTo(KDL_TYPE_CODE_SYSTEM);
		assertThat(docType.getCoding().get(0).getCode()).isEqualTo("kdl1");
		assertThat(docType.getCoding().get(1).getSystem()).isEqualTo(XDS_TYPE_CODE_SYSTEM);
		assertThat(docType.getCoding().get(1).getCode()).isEqualTo("xdsType1");

		List<CodeableConcept> categories = documentReference.getCategory();
		assertThat(categories).hasSize(1);
		CodeableConcept catConcept = categories.get(0);
		assertThat(catConcept.getCoding()).hasSize(1);
		assertThat(catConcept.getCoding().get(0).getSystem()).isEqualTo(XDS_CLASS_CODE_SYSTEM);
		assertThat(catConcept.getCoding().get(0).getCode()).isEqualTo("xdsClass1");

		assertThat(outcome.getIssue()).isEmpty();
	}

	@Test
	void testMapKdlAndXdsCodings_CompositionProvidesXdsTypeAndXdsClass() {
		Composition composition = new Composition();
		CodeableConcept compType = new CodeableConcept();
		Coding kdlCoding = new Coding().setSystem(KDL_TYPE_CODE_SYSTEM).setCode("kdl2");
		Coding xdsProvidedCoding = new Coding().setSystem(XDS_TYPE_CODE_SYSTEM).setCode("providedXds");
		compType.addCoding(kdlCoding);
		compType.addCoding(xdsProvidedCoding);
		composition.setType(compType);

		CodeableConcept catConcept = new CodeableConcept();
		Coding xdsClassCoding = new Coding().setSystem(XDS_CLASS_CODE_SYSTEM).setCode("providedXdsClass");
		catConcept.addCoding(xdsClassCoding);
		composition.setCategory(List.of(catConcept));

		DocumentReference documentReference = new DocumentReference();
		OperationOutcome outcome = new OperationOutcome();

		mapper.mapKdlAndXdsCodings(composition, documentReference, outcome);

		CodeableConcept docType = documentReference.getType();
		assertThat(docType).isNotNull();
		assertThat(docType.getCoding()).hasSize(2);
		assertThat(docType.getCoding().get(0).getSystem()).isEqualTo(KDL_TYPE_CODE_SYSTEM);
		assertThat(docType.getCoding().get(0).getCode()).isEqualTo("kdl2");
		assertThat(docType.getCoding().get(1).getSystem()).isEqualTo(XDS_TYPE_CODE_SYSTEM);
		assertThat(docType.getCoding().get(1).getCode()).isEqualTo("providedXds");

		List<CodeableConcept> categories = documentReference.getCategory();
		assertThat(categories).hasSize(1);
		CodeableConcept catResult = categories.get(0);
		assertThat(catResult.getCoding()).hasSize(1);
		assertThat(catResult.getCoding().get(0).getSystem()).isEqualTo(XDS_CLASS_CODE_SYSTEM);
		assertThat(catResult.getCoding().get(0).getCode()).isEqualTo("providedXdsClass");

		assertThat(outcome.getIssue()).isEmpty();
	}

	@Test
	void testMapKdlAndXdsCodings_NoMappingForXdsTypeOrClass() {
		Composition composition = new Composition();
		CodeableConcept compType = new CodeableConcept();
		Coding kdlCoding = new Coding().setSystem(KDL_TYPE_CODE_SYSTEM).setCode("kdl3");
		compType.addCoding(kdlCoding);
		composition.setType(compType);
		composition.setCategory(Collections.emptyList());

		DocumentReference documentReference = new DocumentReference();
		OperationOutcome outcome = new OperationOutcome();

		ConceptMap typeConceptMap = new ConceptMap();
		when(kdlCodeMapper.getTypeCodeConceptMap()).thenReturn(typeConceptMap);
		when(kdlCodeMapper.findTargetCoding(typeConceptMap, "kdl3", KDL_TYPE_CODE_SYSTEM, XDS_TYPE_CODE_SYSTEM))
			.thenReturn(null);

		ConceptMap classConceptMap = new ConceptMap();
		when(kdlCodeMapper.getClassCodeConceptMap()).thenReturn(classConceptMap);
		when(kdlCodeMapper.findTargetCoding(classConceptMap, "kdl3", KDL_TYPE_CODE_SYSTEM, XDS_CLASS_CODE_SYSTEM))
			.thenReturn(null);

		mapper.mapKdlAndXdsCodings(composition, documentReference, outcome);

		CodeableConcept docType = documentReference.getType();
		assertThat(docType).isNotNull();
		assertThat(docType.getCoding()).hasSize(1);
		assertThat(docType.getCoding().get(0).getSystem()).isEqualTo(KDL_TYPE_CODE_SYSTEM);
		assertThat(docType.getCoding().get(0).getCode()).isEqualTo("kdl3");

		List<CodeableConcept> categories = documentReference.getCategory();
		assertThat(categories).isEmpty();

		assertThat(outcome.getIssue()).isEmpty();
	}
}
