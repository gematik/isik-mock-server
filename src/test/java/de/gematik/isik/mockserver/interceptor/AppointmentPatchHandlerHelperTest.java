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
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppointmentPatchHandlerHelperTest {

	private DaoRegistry daoRegistry;
	private AppointmentPatchHandlerHelper appointmentPatchHandlerHelper;

	@BeforeEach
	void setup() {
		daoRegistry = mock(DaoRegistry.class);
		appointmentPatchHandlerHelper = new AppointmentPatchHandlerHelper(daoRegistry);
	}

	@Test
	void testGetOriginalAppointment() {
		IFhirResourceDao<Appointment> appointmentDaoMock = mock(IFhirResourceDao.class);
		RequestDetails requestDetails = mock(RequestDetails.class);

		Appointment expectedAppointment = new Appointment();
		expectedAppointment.setId("Appointment/123");

		when(daoRegistry.getResourceDao(Appointment.class)).thenReturn(appointmentDaoMock);
		when(appointmentDaoMock.read(new IdType("123"), requestDetails)).thenReturn(expectedAppointment);

		Appointment result = appointmentPatchHandlerHelper.getOriginalAppointment("123", requestDetails);

		assertThat(result).isEqualTo(expectedAppointment);
	}

	@Test
	void testIsParameterPresentTrue() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-patch-appointment-parameters.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);

		boolean isSlotParameterPresent = appointmentPatchHandlerHelper.isParameterPresent(parameters, "Appointment.slot");

		assertThat(isSlotParameterPresent).isTrue();
	}

	@Test
	void testIsParameterPresentFalse() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-patch-appointment-parameters.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);

		boolean isSlotParameterPresent = appointmentPatchHandlerHelper.isParameterPresent(parameters, "Appointment.unknown-parameter");

		assertThat(isSlotParameterPresent).isFalse();
	}

	@Test
	void testGetReferenceFromParameters() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-patch-appointment-parameters.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);

		String slotReference = appointmentPatchHandlerHelper.getReferenceFromParameters(parameters, "Appointment.slot");

		assertThat(slotReference).isEqualTo("Slot/Free-Block");
	}

	@Test
	void testGetReferenceFromParametersThrowsException1() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-patch-appointment-parameters.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);

		assertThatThrownBy(() -> appointmentPatchHandlerHelper.getReferenceFromParameters(parameters, "Appointment.unknown-location"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("The 'Appointment.unknown-location' parameter is missing or invalid in the provided Parameters resource.");
	}

	@Test
	void testGetReferenceFromParametersThrowsException2() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/patch-appointment-parameters-missing-values.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);

		assertThatThrownBy(() -> appointmentPatchHandlerHelper.getReferenceFromParameters(parameters, "Appointment.slot"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("The 'valueReference' part with a valid Reference is missing in the 'Appointment.slot' operation.");
	}

	@Test
	void testGetDateFromParameters() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-patch-appointment-parameters.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);

		String dateString = appointmentPatchHandlerHelper.getDateFromParameters(parameters, "Appointment.start");

		assertThat(dateString).isEqualTo("2027-01-01T15:00:00.000+01:00");
	}

	@Test
	void testGetDateFromParametersThrowsException1() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-patch-appointment-parameters.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);

		assertThatThrownBy(() -> appointmentPatchHandlerHelper.getDateFromParameters(parameters, "Appointment.unknown-location"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("The 'Appointment.unknown-location' parameter is missing or invalid in the provided Parameters resource.");
	}

	@Test
	void testGetDateFromParametersThrowsException2() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/patch-appointment-parameters-missing-values.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);

		assertThatThrownBy(() -> appointmentPatchHandlerHelper.getDateFromParameters(parameters, "Appointment.start"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("The 'value' part with a valid DateTime is missing in the 'Appointment.start' operation.");
	}

	@Test
	void testHasSlotReferenceChangedValid() {
		Appointment originalAppointment = mock(Appointment.class);
		Reference slotReference = new Reference("Slot/Original-Slot");
		when(originalAppointment.getSlot()).thenReturn(Collections.singletonList(slotReference));
		OperationOutcome outcome = new OperationOutcome();
		appointmentPatchHandlerHelper.validateSlotReferenceUnchanged("Slot/Original-Slot", originalAppointment, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
	}

	@Test
	void testHasSlotReferenceChangedInvalid() {
		Appointment originalAppointment = mock(Appointment.class);
		Reference slotReference = new Reference("Slot/Original-Slot");
		when(originalAppointment.getSlot()).thenReturn(Collections.singletonList(slotReference));
		OperationOutcome outcome = new OperationOutcome();
		appointmentPatchHandlerHelper.validateSlotReferenceUnchanged("Slot/Updated-Slot", originalAppointment, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		boolean hasExpectedMessage = outcome.getIssue().stream()
				.anyMatch(issue -> issue.getDiagnostics().contains("Appointment.slot MUST NOT be changed."));
		assertThat(hasExpectedMessage).isTrue();
	}

	@Test
	void testHasPatientReferenceChangedValid() {
		Appointment originalAppointment = mock(Appointment.class);
		Reference patientReference = new Reference("Patient/Original-Patient");
		Appointment.AppointmentParticipantComponent participant = new Appointment.AppointmentParticipantComponent();
		participant.setActor(patientReference);
		when(originalAppointment.getParticipant()).thenReturn(Collections.singletonList(participant));
		OperationOutcome outcome = new OperationOutcome();
		appointmentPatchHandlerHelper.validatePatientReferenceUnchanged("Patient/Original-Patient", originalAppointment, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
	}

	@Test
	void testHasPatientReferenceChangedInvalid() {
		Appointment originalAppointment = mock(Appointment.class);
		Reference patientReference = new Reference("Patient/Original-Patient");
		Appointment.AppointmentParticipantComponent participant = new Appointment.AppointmentParticipantComponent();
		participant.setActor(patientReference);
		when(originalAppointment.getParticipant()).thenReturn(Collections.singletonList(participant));
		OperationOutcome outcome = new OperationOutcome();
		appointmentPatchHandlerHelper.validatePatientReferenceUnchanged("Patient/Updated-Patient", originalAppointment, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		boolean hasExpectedMessage = outcome.getIssue().stream()
				.anyMatch(issue -> issue.getDiagnostics().contains("The Patient Reference MUST NOT be changed."));
		assertThat(hasExpectedMessage).isTrue();
	}

	@Test
	void testIsReferencedPatientValidValid() {
		IFhirResourceDao<Patient> patientDaoMock = mock(IFhirResourceDao.class);
		RequestDetails requestDetails = mock(RequestDetails.class);

		Patient patchPatient = new Patient();
		patchPatient.setId("Patient/123");
		patchPatient.setActive(true);

		when(daoRegistry.getResourceDao(Patient.class)).thenReturn(patientDaoMock);
		when(patientDaoMock.read(new IdType("123"), requestDetails)).thenReturn(patchPatient);
		OperationOutcome outcome = new OperationOutcome();
		appointmentPatchHandlerHelper.validateReferencedPatientActive("123", outcome, requestDetails);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
	}

	@Test
	void testIsReferencedPatientValidInvalid() {
		IFhirResourceDao<Patient> patientDaoMock = mock(IFhirResourceDao.class);
		RequestDetails requestDetails = mock(RequestDetails.class);

		Patient patchPatient = new Patient();
		patchPatient.setId("Patient/123");
		patchPatient.setActive(false);

		when(daoRegistry.getResourceDao(Patient.class)).thenReturn(patientDaoMock);
		when(patientDaoMock.read(new IdType("123"), requestDetails)).thenReturn(patchPatient);
		OperationOutcome outcome = new OperationOutcome();
		appointmentPatchHandlerHelper.validateReferencedPatientActive("123", outcome, requestDetails);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		boolean hasExpectedMessage = outcome.getIssue().stream()
				.anyMatch(issue -> issue.getDiagnostics().contains("The referenced Patient has 'active=false' but must be 'active=true'"));
		assertThat(hasExpectedMessage).isTrue();
	}

	@Test
	void testHasStartChangedValid() {
		Appointment originalAppointment = new Appointment();
		Date originalStartDate = Date.from(OffsetDateTime.parse("2027-01-01T15:00:00.000+01:00").toInstant());
		originalAppointment.setStart(originalStartDate);
		String patchStartDateString = "2027-01-01T15:00:00.000+01:00";
		OperationOutcome outcome = new OperationOutcome();

		appointmentPatchHandlerHelper.validateStartUnchanged(patchStartDateString, originalAppointment, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
	}

	@Test
	void testHasStartChangedInvalid() {
		Appointment originalAppointment = new Appointment();
		Date originalStartDate = Date.from(OffsetDateTime.parse("2027-01-01T15:00:00.000+01:00").toInstant());
		originalAppointment.setStart(originalStartDate);
		String patchStartDateString = "2027-02-01T15:00:00.000+01:00";
		OperationOutcome outcome = new OperationOutcome();

		appointmentPatchHandlerHelper.validateStartUnchanged(patchStartDateString, originalAppointment, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		boolean hasExpectedMessage = outcome.getIssue().stream()
				.anyMatch(issue -> issue.getDiagnostics().contains("Appointment.start MUST NOT be changed."));
		assertThat(hasExpectedMessage).isTrue();
	}

	@Test
	void testHasEndChangedValid() {
		Appointment originalAppointment = new Appointment();
		Date originalEndDate = Date.from(OffsetDateTime.parse("2027-01-01T15:00:00.000+01:00").toInstant());
		originalAppointment.setEnd(originalEndDate);
		String patchEndDateString = "2027-01-01T15:00:00.000+01:00";
		OperationOutcome outcome = new OperationOutcome();

		appointmentPatchHandlerHelper.validateEndUnchanged(patchEndDateString, originalAppointment, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isFalse();
	}

	@Test
	void testHasEndChangedInvalid() {
		Appointment originalAppointment = new Appointment();
		Date originalEndDate = Date.from(OffsetDateTime.parse("2027-01-01T15:00:00.000+01:00").toInstant());
		originalAppointment.setEnd(originalEndDate);
		String patchEndDateString = "2027-02-01T15:00:00.000+01:00";
		OperationOutcome outcome = new OperationOutcome();

		appointmentPatchHandlerHelper.validateEndUnchanged(patchEndDateString, originalAppointment, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		boolean hasExpectedMessage = outcome.getIssue().stream()
				.anyMatch(issue -> issue.getDiagnostics().contains("Appointment.end MUST NOT be changed."));
		assertThat(hasExpectedMessage).isTrue();
	}

	@Test
	void testOperationTypeNotReplaceLeadsToErrorsInOperationOutcome() {
		String body = ResourceLoadingHelper.loadResourceAsString("fhir-examples/invalid/invalid-patch-parameters-type.json");
		Parameters parameters = (Parameters) FhirContext.forR4().newJsonParser().parseResource(body);
		OperationOutcome outcome = new OperationOutcome();

		appointmentPatchHandlerHelper.validateParametersOfTypeReplace(parameters, outcome);

		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		boolean hasExpectedMessage = outcome.getIssue().stream()
				.anyMatch(issue -> issue.getDiagnostics().contains("This server only supports operation type 'replace' but was 'add'"));
		assertThat(hasExpectedMessage).isTrue();
	}
}
