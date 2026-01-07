package de.gematik.isik.mockserver.interceptor;

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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import de.gematik.isik.mockserver.helper.ResourceLoadingHelper;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppointmentPatchHandlerTest {

	private DaoRegistry daoMock;
	private AppointmentPatchHandler appointmentPatchHandler;

	@BeforeEach
	void setup() {
		daoMock = mock(DaoRegistry.class);
		AppointmentPatchHandlerHelper appointmentPatchHandlerHelper = new AppointmentPatchHandlerHelper(daoMock);
		appointmentPatchHandler = new AppointmentPatchHandler(appointmentPatchHandlerHelper);
	}

	@Test
	void testIfInvalidPatchRequestLeadsToErrorsInOperationOutcome() {
		String originalAppointmentBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/valid-appointment.json");
		Appointment originalAppointment = (Appointment) FhirContext.forR4().newJsonParser().parseResource(originalAppointmentBody);
		String parametersBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-patch-appointment-parameters.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(parametersBody);
		String patientBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/valid-patient.json");
		Patient patient = (Patient) FhirContext.forR4().newJsonParser().parseResource(patientBody);

		RequestDetails requestDetails = mock(RequestDetails.class);
		IFhirResourceDao<Appointment> appointmentDaoMock = mock(IFhirResourceDao.class);
		when(daoMock.getResourceDao(Appointment.class)).thenReturn(appointmentDaoMock);
		when(appointmentDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(originalAppointment);
		IFhirResourceDao<Patient> patientDaoMock = mock(IFhirResourceDao.class);
		when(daoMock.getResourceDao(Patient.class)).thenReturn(patientDaoMock);
		when(patientDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(patient);
		OperationOutcome outcome = appointmentPatchHandler.handle(parameters, requestDetails);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
	}

	@Test
	void testValidPatchRequest() {
		String originalAppointmentBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/valid-appointment.json");
		Appointment originalAppointment = (Appointment) FhirContext.forR4().newJsonParser().parseResource(originalAppointmentBody);
		String parametersBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/valid-patch-appointment-parameters.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(parametersBody);

		RequestDetails requestDetails = mock(RequestDetails.class);
		IFhirResourceDao<Appointment> appointmentDaoMock = mock(IFhirResourceDao.class);
		when(daoMock.getResourceDao(Appointment.class)).thenReturn(appointmentDaoMock);
		when(appointmentDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(originalAppointment);
		OperationOutcome outcome = appointmentPatchHandler.handle(parameters, requestDetails);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
	}
}
