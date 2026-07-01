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

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientMergeOperationProviderTest {

	private PatientMergeOperationProvider provider;

	@Mock
	private DaoRegistry daoRegistry;

	@Mock
	private SubscriptionTopicDispatcher subscriptionTopicDispatcher;

	@Mock
	private IFhirResourceDao<Patient> patientDao;

	private AutoCloseable closeable;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);
		when(daoRegistry.getResourceDao(ResourceType.Patient.name())).thenReturn(patientDao);
		provider = new PatientMergeOperationProvider(daoRegistry, subscriptionTopicDispatcher);
	}

	private Patient createPatient(String id, boolean withPid) {
		Patient p = new Patient();
		p.setId(id);
		if (withPid) {
			Identifier pid = new Identifier();
			pid.getType().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setCode("MR");
			pid.setSystem("urn:system");
			pid.setValue("PID-" + id);
			p.addIdentifier(pid);
		}
		return p;
	}

	@Test
	void testMergeSuccessWithIdentifiers() {
		Patient source = createPatient("source-id", true);
		Patient target = createPatient("target-id", true);

		Identifier sourceIdentifier = new Identifier().setSystem("sys").setValue("source-val");
		Identifier targetIdentifier = new Identifier().setSystem("sys").setValue("target-val");

		IBundleProvider sourceBundle = mock(IBundleProvider.class);
		IBundleProvider targetBundle = mock(IBundleProvider.class);

		when(sourceBundle.getResources(0, 2)).thenReturn(List.of(source));
		when(targetBundle.getResources(0, 2)).thenReturn(List.of(target));

		when(patientDao.search(argThat(arg -> arg != null && arg.get(Patient.SP_IDENTIFIER) != null), any(RequestDetails.class))).thenAnswer(invocation -> {
			SearchParameterMap map = invocation.getArgument(0);
			String val = map.get(Patient.SP_IDENTIFIER).get(0).get(0).getValueAsQueryToken();
			if (val.contains("source-val")) {
				return sourceBundle;
			} else {
				return targetBundle;
			}
		});

		Parameters params = provider.patientMerge(
				null, List.of(sourceIdentifier),
				null, List.of(targetIdentifier),
				null, new BooleanType(false));

		assertThat(params).isNotNull();
	}

	@Test
	void testMergePreviewOnly() {
		Patient source = createPatient("source-id", true);
		Patient target = createPatient("target-id", true);

		Reference sourceRef = new Reference("Patient/source-id");
		Reference targetRef = new Reference("Patient/target-id");

		when(patientDao.read(eq(new IdType("Patient/source-id")), any(RequestDetails.class))).thenReturn(source);
		when(patientDao.read(eq(new IdType("Patient/target-id")), any(RequestDetails.class))).thenReturn(target);

		Parameters params = provider.patientMerge(
				sourceRef, null,
				targetRef, null,
				null, new BooleanType(true));

		assertThat(params).isNotNull();

		verify(patientDao, never()).update(any(), any(RequestDetails.class));
		verify(subscriptionTopicDispatcher, never()).dispatch(anyString(), anyList(), any());
	}

	@Test
	void testMergeWithResultPatientReplacement() {
		Patient source = createPatient("source-id", true);
		Patient target = createPatient("target-id", true);
		Patient resultMock = createPatient("replaced-target-id", false);

		Reference sourceRef = new Reference("Patient/source-id");
		Reference targetRef = new Reference("Patient/target-id");

		when(patientDao.read(eq(new IdType("Patient/source-id")), any(RequestDetails.class))).thenReturn(source);
		when(patientDao.read(eq(new IdType("Patient/target-id")), any(RequestDetails.class))).thenReturn(target);

		Parameters params = provider.patientMerge(
				sourceRef, null,
				targetRef, null,
				resultMock, null);

		assertThat(params).isNotNull();
	}

	@Test
	void testMissingPidThrowsPreconditionFailed() {
		Patient source = createPatient("source-id", false); // no PID
		Patient target = createPatient("target-id", true);

		Reference sourceRef = new Reference("Patient/source-id");
		Reference targetRef = new Reference("Patient/target-id");

		when(patientDao.read(eq(new IdType("Patient/source-id")), any(RequestDetails.class))).thenReturn(source);
		when(patientDao.read(eq(new IdType("Patient/target-id")), any(RequestDetails.class))).thenReturn(target);

		assertThatThrownBy(() -> provider.patientMerge(sourceRef, null, targetRef, null, null, null))
				.isInstanceOf(PreconditionFailedException.class)
				.hasMessageContaining("Patients need a populated PID (Identifier.type = MR)");
	}

	@Test
	void testInvalidRequestMissingParameters() {
		// Both null
		assertThatThrownBy(() -> provider.patientMerge(null, null, new Reference("Patient/target-id"), null, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("Exactly one of 'source-patient' or 'source-patient-identifier' must be provided");
	}

	@Test
	void testInvalidRequestMutualExclusion() {
		// Both set
		Reference sourceRef = new Reference("Patient/source-id");
		List<Identifier> identifiers = List.of(new Identifier().setValue("val"));

		assertThatThrownBy(() -> provider.patientMerge(sourceRef, identifiers, new Reference("Patient/target-id"), null, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("Exactly one of 'source-patient' or 'source-patient-identifier' must be provided");
	}

	@Test
	void testUnprocessableEntityZeroMatches() {
		List<Identifier> sourceIdentifiers = List.of(new Identifier().setSystem("sys").setValue("source-val"));
		IBundleProvider emptyBundle = mock(IBundleProvider.class);
		when(emptyBundle.getResources(0, 2)).thenReturn(Collections.emptyList());
		when(patientDao.search(any(), any(RequestDetails.class))).thenReturn(emptyBundle);

		assertThatThrownBy(() -> provider.patientMerge(null, sourceIdentifiers, new Reference("Patient/target-id"), null, null, null))
				.isInstanceOf(UnprocessableEntityException.class)
				.hasMessageContaining("source-patient-identifier must resolve to exactly one Patient, but matched 0");
	}

	@Test
	void testUnprocessableEntityMultipleMatches() {
		List<Identifier> sourceIdentifiers = List.of(new Identifier().setSystem("sys").setValue("source-val"));
		IBundleProvider multipleBundle = mock(IBundleProvider.class);
		Patient p1 = createPatient("p1", true);
		Patient p2 = createPatient("p2", true);
		when(multipleBundle.getResources(0, 2)).thenReturn(List.of(p1, p2));
		when(patientDao.search(any(), any(RequestDetails.class))).thenReturn(multipleBundle);

		assertThatThrownBy(() -> provider.patientMerge(null, sourceIdentifiers, new Reference("Patient/target-id"), null, null, null))
				.isInstanceOf(UnprocessableEntityException.class)
				.hasMessageContaining("source-patient-identifier must resolve to exactly one Patient, but matched 2");
	}
}

