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
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IsiKBerichtSubSystemeRelatesToMapperTest {

	private IsiKBerichtSubSystemeRelatesToMapper mapper;
    private RequestDetails requestDetailsMock;
	private IFhirResourceDao<DocumentReference> documentReferenceDaoMock;

	@BeforeEach
	void setup() {
      DaoRegistry daoRegistryMock = mock(DaoRegistry.class);
		requestDetailsMock = mock(RequestDetails.class);
		documentReferenceDaoMock = mock(IFhirResourceDao.class);
		mapper = new IsiKBerichtSubSystemeRelatesToMapper(daoRegistryMock);
		when(daoRegistryMock.getResourceDao(DocumentReference.class)).thenReturn(documentReferenceDaoMock);
	}

	@Test
	void testMapRelatesToComponents_NoRelatesTo() {
		Composition composition = new Composition();
		composition.setRelatesTo(Collections.emptyList());

		List<DocumentReference.DocumentReferenceRelatesToComponent> result =
			mapper.mapRelatesToComponents(composition, requestDetailsMock);

		assertThat(result).isEmpty();
	}

	@Test
	void testMapRelatesToComponents_NullTarget() {
		Composition composition = new Composition();
		Composition.CompositionRelatesToComponent relatesTo = new Composition.CompositionRelatesToComponent();
		relatesTo.setTarget(null);
		composition.setRelatesTo(List.of(relatesTo));

		List<DocumentReference.DocumentReferenceRelatesToComponent> result =
			mapper.mapRelatesToComponents(composition, requestDetailsMock);

		assertThat(result).isEmpty();
	}

	@Test
	void testMapRelatesToComponents_InvalidIdentifier_MissingSystemOrValue() {
		Composition composition = new Composition();
		Composition.CompositionRelatesToComponent relatesTo = new Composition.CompositionRelatesToComponent();
		Reference ref = new Reference();
		Identifier identifier = new Identifier();
		ref.setIdentifier(identifier);
		relatesTo.setTarget(ref);
		// Set an arbitrary code (the actual value is not important since mapping is skipped)
		relatesTo.setCode(Composition.DocumentRelationshipType.REPLACES);
		composition.setRelatesTo(List.of(relatesTo));

		List<DocumentReference.DocumentReferenceRelatesToComponent> result =
			mapper.mapRelatesToComponents(composition, requestDetailsMock);

		assertThat(result).isEmpty();
	}

	@Test
	void testMapRelatesToComponents_ValidReference_SingleDocumentReferenceFound() {
		String expectedSystem = "http://example.org/system";
		String expectedValue = "DOC123";
		Composition composition = createCompositionWithRelatesTo(true, expectedSystem, expectedValue,
			Composition.DocumentRelationshipType.REPLACES);

		DocumentReference docRef = new DocumentReference();
		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(
			Collections.singletonList(docRef)
		);
		when(documentReferenceDaoMock.search(any(SearchParameterMap.class), eq(requestDetailsMock)))
			.thenReturn(bundleProvider);

		List<DocumentReference.DocumentReferenceRelatesToComponent> result =
			mapper.mapRelatesToComponents(composition, requestDetailsMock);

		assertThat(result).hasSize(1);
		DocumentReference.DocumentReferenceRelatesToComponent mapped = result.get(0);
		assertThat(mapped.getCode())
			.isEqualTo(DocumentReference.DocumentRelationshipType.fromCode(
				Composition.DocumentRelationshipType.REPLACES.toCode()));
		assertThat(mapped.getTarget().getResource()).isEqualTo(docRef);
	}

	@Test
	void testMapRelatesToComponents_ValidIdentifierAsTarget_SingleDocumentReferenceFound() {
		String expectedSystem = "http://example.org/system";
		String expectedValue = "DOC456";
		Composition composition = createCompositionWithRelatesTo(false, expectedSystem, expectedValue,
			Composition.DocumentRelationshipType.APPENDS);

		DocumentReference docRef = new DocumentReference();
		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(
			Collections.singletonList(docRef)
		);
		when(documentReferenceDaoMock.search(any(SearchParameterMap.class), eq(requestDetailsMock)))
			.thenReturn(bundleProvider);

		List<DocumentReference.DocumentReferenceRelatesToComponent> result =
			mapper.mapRelatesToComponents(composition, requestDetailsMock);

		assertThat(result).hasSize(1);
		DocumentReference.DocumentReferenceRelatesToComponent mapped = result.get(0);
		assertThat(mapped.getCode())
			.isEqualTo(DocumentReference.DocumentRelationshipType.fromCode(
				Composition.DocumentRelationshipType.APPENDS.toCode()));
		assertThat(mapped.getTarget().getResource()).isEqualTo(docRef);
	}

	private Composition createCompositionWithRelatesTo(boolean asReference, String system, String value,
																		Composition.DocumentRelationshipType relationshipType) {
		Composition composition = new Composition();
		Composition.CompositionRelatesToComponent relatesTo = new Composition.CompositionRelatesToComponent();
		if (asReference) {
			Reference ref = new Reference();
			Identifier identifier = new Identifier();
			identifier.setSystem(system);
			identifier.setValue(value);
			ref.setIdentifier(identifier);
			relatesTo.setTarget(ref);
		} else {
			Identifier identifier = new Identifier();
			identifier.setSystem(system);
			identifier.setValue(value);
			relatesTo.setTarget(identifier);
		}
		relatesTo.setCode(relationshipType);
		composition.setRelatesTo(List.of(relatesTo));
		return composition;
	}

	@Test
	void testMapRelatesToComponents_NoServerDocumentReferenceFound() {
		Composition composition = new Composition();
		Composition.CompositionRelatesToComponent relatesTo = new Composition.CompositionRelatesToComponent();
		Reference ref = new Reference();
		Identifier identifier = new Identifier();
		String expectedSystem = "http://example.org/system";
		String expectedValue = "DOC789";
		identifier.setSystem(expectedSystem);
		identifier.setValue(expectedValue);
		ref.setIdentifier(identifier);
		relatesTo.setTarget(ref);
		relatesTo.setCode(Composition.DocumentRelationshipType.TRANSFORMS);
		composition.setRelatesTo(List.of(relatesTo));

		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(Collections.emptyList());
		when(documentReferenceDaoMock.search(any(SearchParameterMap.class), eq(requestDetailsMock)))
			.thenReturn(bundleProvider);

		List<DocumentReference.DocumentReferenceRelatesToComponent> result =
			mapper.mapRelatesToComponents(composition, requestDetailsMock);

		assertThat(result).isEmpty();
	}

	@Test
	void testMapRelatesToComponents_MultipleServerDocumentReferencesFound() {
		Composition composition = new Composition();
		Composition.CompositionRelatesToComponent relatesTo = new Composition.CompositionRelatesToComponent();
		Reference ref = new Reference();
		Identifier identifier = new Identifier();
		String expectedSystem = "http://example.org/system";
		String expectedValue = "DOC999";
		identifier.setSystem(expectedSystem);
		identifier.setValue(expectedValue);
		ref.setIdentifier(identifier);
		relatesTo.setTarget(ref);
		relatesTo.setCode(Composition.DocumentRelationshipType.TRANSFORMS);
		composition.setRelatesTo(List.of(relatesTo));

		DocumentReference docRef1 = new DocumentReference();
		DocumentReference docRef2 = new DocumentReference();
		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(List.of(docRef1, docRef2));
		when(documentReferenceDaoMock.search(any(SearchParameterMap.class), eq(requestDetailsMock)))
			.thenReturn(bundleProvider);

		List<DocumentReference.DocumentReferenceRelatesToComponent> result =
			mapper.mapRelatesToComponents(composition, requestDetailsMock);

		assertThat(result).isEmpty();
	}
}
