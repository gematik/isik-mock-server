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
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IsikBerichtSubSystemeSubjectMapperTest {

	private IsikBerichtSubSystemeSubjectMapper mapper;
	private DaoRegistry daoRegistryMock;
	private RequestDetails requestDetailsMock;
	private IFhirResourceDao<Patient> patientDaoMock;

	@BeforeEach
	void setup() {
		daoRegistryMock = mock(DaoRegistry.class);
		requestDetailsMock = mock(RequestDetails.class);
		patientDaoMock = mock(IFhirResourceDao.class);
		mapper = new IsikBerichtSubSystemeSubjectMapper(daoRegistryMock);
		when(daoRegistryMock.getResourceDao(Patient.class)).thenReturn(patientDaoMock);
	}

	@Test
	void testMapSubject_NoPatientResource() {
		Composition composition = new Composition();
		composition.setSubject(new Reference());

		OperationOutcome outcome = new OperationOutcome();

		Reference result = mapper.mapSubject(composition, outcome, requestDetailsMock);

		assertThat(result).isNull();
		assertThat(outcome.getIssue())
			.isNotEmpty()
			.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
			.contains("No Patient resource found in the Bundle for Composition.subject");

		verify(daoRegistryMock, never()).getResourceDao(Patient.class);
	}

	@Test
	void testMapSubject_NoMatchingIdentifier() {
		Composition composition = new Composition();
		Patient patient = new Patient();
		Identifier id = new Identifier();
		id.setSystem("http://invalid.system");
		id.setValue("12345");
		id.setType(new CodeableConcept(new Coding("http://invalid.system", "XYZ", "Unknown")));
		patient.setIdentifier(Collections.singletonList(id));
		composition.setSubject(new Reference(patient));

		OperationOutcome outcome = new OperationOutcome();

		Reference result = mapper.mapSubject(composition, outcome, requestDetailsMock);

		assertThat(result).isNull();
		assertThat(outcome.getIssue())
			.isNotEmpty()
			.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
			.contains("No PID, GKV, or PKV identifier found in the Patient resource referenced by Composition.subject");

		verify(daoRegistryMock, never()).getResourceDao(Patient.class);
	}

	@Test
	void testMapSubject_NoServerPatientFound() {
		Composition composition = new Composition();
		Patient patient = new Patient();
		Identifier id = new Identifier();
		id.setSystem("http://terminology.hl7.org/CodeSystem/v2-0203");
		id.setValue("MR123");
		id.setType(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", "MR", "Medical Record")));
		patient.setIdentifier(Collections.singletonList(id));
		composition.setSubject(new Reference(patient));

		OperationOutcome outcome = new OperationOutcome();

		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(Collections.emptyList());
		when(patientDaoMock.search(any(SearchParameterMap.class), eq(requestDetailsMock))).thenReturn(bundleProvider);

		Reference result = mapper.mapSubject(composition, outcome, requestDetailsMock);

		assertThat(result).isNull();
		assertThat(outcome.getIssue())
			.isNotEmpty()
			.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
			.anyMatch(diag -> diag.contains("Invalid number of Patients found on server with identifier:"));
	}

	@Test
	void testMapSubject_MultipleServerPatientsFound() {
		Composition composition = new Composition();
		Patient patient = new Patient();
		Identifier id = new Identifier();
		id.setSystem("http://fhir.de/CodeSystem/identifier-type-de-basis");
		id.setValue("GKV123");
		id.setType(new CodeableConcept(new Coding("http://fhir.de/CodeSystem/identifier-type-de-basis", "GKV", "GKV Identifier")));
		patient.setIdentifier(Collections.singletonList(id));
		composition.setSubject(new Reference(patient));

		OperationOutcome outcome = new OperationOutcome();

		Patient patient1 = new Patient();
		Patient patient2 = new Patient();
		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(List.of(patient1, patient2));
		when(patientDaoMock.search(any(SearchParameterMap.class), eq(requestDetailsMock))).thenReturn(bundleProvider);

		Reference result = mapper.mapSubject(composition, outcome, requestDetailsMock);

		assertThat(result).isNull();
		assertThat(outcome.getIssue())
			.isNotEmpty()
			.extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
			.anyMatch(diag -> diag.contains("Invalid number of Patients found on server with identifier:"));
	}

	@Test
	void testMapSubject_SingleServerPatientFound() {
		Composition composition = new Composition();
		Patient patient = new Patient();
		Identifier id = new Identifier();
		id.setSystem("http://terminology.hl7.org/CodeSystem/v2-0203");
		id.setValue("MR999");
		id.setType(new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", "MR", "Medical Record")));
		patient.setIdentifier(Collections.singletonList(id));
		composition.setSubject(new Reference(patient));

		OperationOutcome outcome = new OperationOutcome();

		Patient serverPatient = new Patient();
		IBundleProvider bundleProvider = SimpleBundleProvider.createBundleProvider(Collections.singletonList(serverPatient));
		when(patientDaoMock.search(any(SearchParameterMap.class), eq(requestDetailsMock))).thenReturn(bundleProvider);

		Reference result = mapper.mapSubject(composition, outcome, requestDetailsMock);

		assertThat(result).isNotNull();
		assertThat(result.getResource()).isEqualTo(serverPatient);
		assertThat(outcome.getIssue()).isEmpty();
	}
}
