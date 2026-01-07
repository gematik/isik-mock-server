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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import de.gematik.isik.mockserver.helper.ResourceLoadingHelper;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Schedule;
import org.hl7.fhir.r4.model.Slot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppointmentBookHandlerTest {

	private AppointmentBookHandler handler;
	private final FhirContext ctx = FhirContext.forR4();
	private DaoRegistry daoMock;

	@BeforeEach
	void setup() {
		daoMock = mock(DaoRegistry.class);
		AppointmentBookHandlerHelper appointmentBookHandlerHelper = new AppointmentBookHandlerHelper(daoMock);
		handler = new AppointmentBookHandler(appointmentBookHandlerHelper);
		handler.setCtx(FhirContext.forR4());
	}

	@Test
	void testHandleIncomingAppointmentNoAppointmentInParametersThrowsException() {
		Parameters parameters = new Parameters();
		String body = ctx.newJsonParser().encodeResourceToString(parameters);
		RequestDetails requestDetails = mock(RequestDetails.class);

		assertThatThrownBy(() -> handler.handleIncomingAppointment(body, requestDetails))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Could not find an Appointment resource in incoming Parameters");
	}

	@Test
	void testHandleIncomingAppointmentUnsupportedResourceTypeThrowsException() {
		DocumentReference documentReference = new DocumentReference();
		String body = ctx.newJsonParser().encodeResourceToString(documentReference);
		RequestDetails requestDetails = mock(RequestDetails.class);

		assertThatThrownBy(() -> handler.handleIncomingAppointment(body, requestDetails))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unsupported resource type in incoming body: org.hl7.fhir.r4.model.DocumentReference");
	}

	@Test
	void testHandleIncomingAppointmentParametersMissingSlotAndScheduleThrowsException() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-booking-parameters-missing-slot-and-schedule.json");
		String patientBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/Patient-Mustermann.json");
		Patient patient = (Patient) ctx.newJsonParser().parseResource(patientBody);

		RequestDetails requestDetails = mock(RequestDetails.class);
		IFhirResourceDao<Patient> patientDaoMock = mock(IFhirResourceDao.class);
		when(daoMock.getResourceDao(Patient.class)).thenReturn(patientDaoMock);
		when(patientDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(patient);

		assertThatThrownBy(() -> handler.handleIncomingAppointment(body, requestDetails))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Slot is missing and could not find a Schedule Reference in incoming Parameters");
	}

	@Test
	void testIfInvalidAppointmentBookOperationLeadsToErrorsInOperationOutcome() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-appointment.json");
		String patientBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/Patient-Mustermann.json");
		Patient patient = (Patient) ctx.newJsonParser().parseResource(patientBody);
		String slotBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/Slot-Busy-Block-Example.json");
		Slot slot = (Slot) ctx.newJsonParser().parseResource(slotBody);

		RequestDetails requestDetails = mock(RequestDetails.class);

		IFhirResourceDao<Patient> patientDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Appointment> appointmentDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Slot> slotDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Schedule> scheduleDaoMock = mock(IFhirResourceDao.class);

		when(daoMock.getResourceDao(Patient.class)).thenReturn(patientDaoMock);
		when(daoMock.getResourceDao(Appointment.class)).thenReturn(appointmentDaoMock);
		when(daoMock.getResourceDao(Slot.class)).thenReturn(slotDaoMock);
		when(daoMock.getResourceDao(Schedule.class)).thenReturn(scheduleDaoMock);
		when(patientDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(patient);
		when(slotDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(slot);

		AppointmentHandlerReturnObject outcome = handler.handleIncomingAppointment(body, requestDetails);

		assertThat(outcome.isOperationSuccessful()).isFalse();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome.getOperationOutcome())).isTrue();

		List<String> expectedMessages = List.of(
				"Start date must be in the future.",
				"Status is 'cancelled' but must be 'proposed'.",
				"Wrong CodeSystem for serviceType. Must be 'http://terminology.hl7.org/CodeSystem/service-type'.",
				"Status is 'busy' but must be 'free'.",
				"Appointment times must be within or equal to the start and end times of the referenced slot (Appointment.slot)."
		);
		boolean hasAllExpectedMessages = expectedMessages.stream()
				.allMatch(expectedMessage -> outcome.getOperationOutcome().getIssue().stream()
						.anyMatch(issue -> issue.getDiagnostics().contains(expectedMessage)));

		assertThat(hasAllExpectedMessages).isTrue();
	}

	@Test
	void testValidAppointmentBookOperation() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/valid-appointment.json");
		String patientBody = ResourceLoadingHelper.loadResourceAsString("integration-tests/valid/Patient-PatientinMusterfrau.json");
		Patient patient = (Patient) ctx.newJsonParser().parseResource(patientBody);
		String slotBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/Slot-Free-Block-Example.json");
		Slot slot = (Slot) ctx.newJsonParser().parseResource(slotBody);

		RequestDetails requestDetails = mock(RequestDetails.class);

		IFhirResourceDao<Patient> patientDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Appointment> appointmentDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Slot> slotDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Schedule> scheduleDaoMock = mock(IFhirResourceDao.class);

		when(daoMock.getResourceDao(Patient.class)).thenReturn(patientDaoMock);
		when(daoMock.getResourceDao(Appointment.class)).thenReturn(appointmentDaoMock);
		when(daoMock.getResourceDao(Slot.class)).thenReturn(slotDaoMock);
		when(daoMock.getResourceDao(Schedule.class)).thenReturn(scheduleDaoMock);
		when(patientDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(patient);
		when(slotDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(slot);

		AppointmentHandlerReturnObject outcome = handler.handleIncomingAppointment(body, requestDetails);

		assertThat(outcome.isOperationSuccessful()).isTrue();
	}

	@Test
	void testUnknownCancelledApptIdLeadsToIssueInOperationOutcome() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-appointment-rescheduling-parameters.json");
		String patientBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/Patient-Mustermann.json");
		Patient patient = (Patient) ctx.newJsonParser().parseResource(patientBody);
		String slotBody = ResourceLoadingHelper.loadResourceAsString("fhir-examples/valid/Slot-Free-Block-Example.json");
		Slot slot = (Slot) ctx.newJsonParser().parseResource(slotBody);

		RequestDetails requestDetails = mock(RequestDetails.class);

		IFhirResourceDao<Patient> patientDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Appointment> appointmentDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Slot> slotDaoMock = mock(IFhirResourceDao.class);
		IFhirResourceDao<Schedule> scheduleDaoMock = mock(IFhirResourceDao.class);

		when(appointmentDaoMock.read(
				argThat(id -> id.getIdPart().equals("Unknown-Appointment-ID")),
				argThat(req -> req.equals(requestDetails))
		)).thenThrow(new ResourceNotFoundException("Schedule not found"));

		when(daoMock.getResourceDao(Patient.class)).thenReturn(patientDaoMock);
		when(daoMock.getResourceDao(Appointment.class)).thenReturn(appointmentDaoMock);
		when(daoMock.getResourceDao(Slot.class)).thenReturn(slotDaoMock);
		when(daoMock.getResourceDao(Schedule.class)).thenReturn(scheduleDaoMock);
		when(patientDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(patient);
		when(slotDaoMock.read(any(IdType.class), eq(requestDetails)))
				.thenReturn(slot);

		AppointmentHandlerReturnObject outcome = handler.handleIncomingAppointment(body, requestDetails);

		assertThat(outcome.isOperationSuccessful()).isFalse();
		assertThat(outcome.getOperationOutcome().getIssue())
				.extracting("diagnostics")
				.contains("Appointment for cancellation with ID Appointment/Unknown-Appointment-ID not found (cancelled-appt-id)");
	}
}
