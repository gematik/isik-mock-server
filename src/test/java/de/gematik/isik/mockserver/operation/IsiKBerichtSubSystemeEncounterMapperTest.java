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
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
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

class IsiKBerichtSubSystemeEncounterMapperTest {

	private IsiKBerichtSubSystemeEncounterMapper mapper;
	private DaoRegistry daoRegistryMock;
	private RequestDetails requestDetailsMock;
	private IFhirResourceDao<Encounter> encounterDaoMock;

	@BeforeEach
	void setup() {
		daoRegistryMock = mock(DaoRegistry.class);
		requestDetailsMock = mock(RequestDetails.class);
		encounterDaoMock = mock(IFhirResourceDao.class);
		mapper = new IsiKBerichtSubSystemeEncounterMapper(daoRegistryMock);
		when(daoRegistryMock.getResourceDao(Encounter.class)).thenReturn(encounterDaoMock);
	}

	@Test
	void testMapEncounter_NoEncounterInComposition() {
		Composition composition = new Composition();
		DocumentReference.DocumentReferenceContextComponent contextComponent = new DocumentReference.DocumentReferenceContextComponent();

		mapper.mapEncounter(composition, contextComponent, requestDetailsMock);

		assertThat(contextComponent.getEncounter()).isEmpty();
		verify(daoRegistryMock, never()).getResourceDao((String) any());
	}

	@Test
	void testMapEncounter_EncounterResourceIsNull() {
		Composition composition = new Composition();
		composition.setEncounter(new Reference());
		DocumentReference.DocumentReferenceContextComponent contextComponent = new DocumentReference.DocumentReferenceContextComponent();

		mapper.mapEncounter(composition, contextComponent, requestDetailsMock);

		assertThat(contextComponent.getEncounter()).isEmpty();
		verify(daoRegistryMock, never()).getResourceDao((String) any());
	}

	@Test
	void testMapEncounter_NoMatchingVNIdentifier() {
		Encounter encounter = new Encounter();
		Identifier identifier = new Identifier();
		identifier.setSystem("someSystem");
		identifier.setValue("someValue");
		identifier.setType(new CodeableConcept(new Coding("http://some-system", "XX", "Not VN")));
		encounter.setIdentifier(Collections.singletonList(identifier));

		Composition composition = new Composition();
		composition.setEncounter((Reference) new Reference().setResource(encounter));
		DocumentReference.DocumentReferenceContextComponent contextComponent = new DocumentReference.DocumentReferenceContextComponent();

		mapper.mapEncounter(composition, contextComponent, requestDetailsMock);

		assertThat(contextComponent.getEncounter()).isEmpty();
		verify(daoRegistryMock, never()).getResourceDao((String) any());
	}

	@Test
	void testMapEncounter_SingleEncounterFound() {
		Encounter encounter = new Encounter();
		Identifier identifier = new Identifier();
		String expectedSystem = "http://terminology.hl7.org/CodeSystem/v2-0203";
		String expectedValue = "VN12345";
		identifier.setSystem(expectedSystem);
		identifier.setValue(expectedValue);
		identifier.setType(new CodeableConcept(new Coding(expectedSystem, "VN", "Display")));
		encounter.setIdentifier(Collections.singletonList(identifier));

		Composition composition = new Composition();
		composition.setEncounter((Reference) new Reference().setResource(encounter));
		DocumentReference.DocumentReferenceContextComponent contextComponent = new DocumentReference.DocumentReferenceContextComponent();

		// Prepare a dummy search result containing exactly one Encounter.
		Encounter serverEncounter = new Encounter();
		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(Collections.singletonList(serverEncounter));
		when(encounterDaoMock.search(any(), eq(requestDetailsMock))).thenReturn(bundleProvider);

		mapper.mapEncounter(composition, contextComponent, requestDetailsMock);

		assertThat(contextComponent.getEncounter())
			.hasSize(1)
			.extracting(Reference::getResource)
			.containsExactly(serverEncounter);
	}

	@Test
	void testMapEncounter_NoServerEncounterFound() {
		Encounter encounter = new Encounter();
		Identifier identifier = new Identifier();
		String expectedSystem = "http://terminology.hl7.org/CodeSystem/v2-0203";
		String expectedValue = "VN12345";
		identifier.setSystem(expectedSystem);
		identifier.setValue(expectedValue);
		identifier.setType(new CodeableConcept(new Coding(expectedSystem, "VN", "Display")));
		encounter.setIdentifier(Collections.singletonList(identifier));

		Composition composition = new Composition();
		composition.setEncounter((Reference) new Reference().setResource(encounter));
		DocumentReference.DocumentReferenceContextComponent contextComponent = new DocumentReference.DocumentReferenceContextComponent();

		// Prepare an empty search result.
		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(Collections.emptyList());
		when(encounterDaoMock.search(any(), eq(requestDetailsMock))).thenReturn(bundleProvider);

		mapper.mapEncounter(composition, contextComponent, requestDetailsMock);

		// Since no Encounter was found on the server, the context should remain unchanged.
		assertThat(contextComponent.getEncounter()).isEmpty();
	}

	@Test
	void testMapEncounter_MultipleEncountersFound() {
		Encounter encounter = new Encounter();
		Identifier identifier = new Identifier();
		String expectedSystem = "http://terminology.hl7.org/CodeSystem/v2-0203";
		String expectedValue = "VN12345";
		identifier.setSystem(expectedSystem);
		identifier.setValue(expectedValue);
		identifier.setType(new CodeableConcept(new Coding(expectedSystem, "VN", "Display")));
		encounter.setIdentifier(Collections.singletonList(identifier));

		Composition composition = new Composition();
		composition.setEncounter((Reference) new Reference().setResource(encounter));
		DocumentReference.DocumentReferenceContextComponent contextComponent = new DocumentReference.DocumentReferenceContextComponent();

		// Prepare a search result with two Encounters.
		Encounter serverEncounter1 = new Encounter();
		Encounter serverEncounter2 = new Encounter();
		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(List.of(serverEncounter1, serverEncounter2));
		when(encounterDaoMock.search(any(), eq(requestDetailsMock))).thenReturn(bundleProvider);

		mapper.mapEncounter(composition, contextComponent, requestDetailsMock);

		// In the case of multiple matches, the context is expected to remain unchanged.
		assertThat(contextComponent.getEncounter()).isEmpty();
	}
}
