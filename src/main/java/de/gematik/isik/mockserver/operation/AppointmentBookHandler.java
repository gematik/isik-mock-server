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
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Slot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@Setter
@RequiredArgsConstructor
public class AppointmentBookHandler {

	@Autowired
	private FhirContext ctx;

	@Autowired
	private final AppointmentBookHandlerHelper appointmentBookHandlerHelper;

	private record AppointmentExtractionData(
			Appointment appointment, Reference scheduleReference, Reference cancelledApptId) {}

	public AppointmentHandlerReturnObject handleIncomingAppointment(String body, RequestDetails theRequestDetails) {
		var incomingResource = EncodingEnum.detectEncoding(body).newParser(ctx).parseResource(body);
		var extractionData = extractAppointmentData(incomingResource);
		Appointment incomingAppointment = extractionData.appointment();
		Reference scheduleReference = extractionData.scheduleReference();
		Reference cancelledApptId = extractionData.cancelledApptId();

		OperationOutcome outcome = checkPlausibility(incomingAppointment, cancelledApptId, theRequestDetails);

		if (!appointmentBookHandlerHelper.hasSlot(incomingAppointment)) {
			log.info("Incoming Appointment: Slot is missing");
			createSlotIfNotOverlapping(incomingAppointment, scheduleReference, outcome, theRequestDetails);
		}

		incomingAppointment.setId(UUID.randomUUID().toString());
		incomingAppointment.setStatus(Appointment.AppointmentStatus.BOOKED);

		if (OperationOutcomeUtils.hasErrorIssue(outcome)) {
			return new AppointmentHandlerReturnObject(null, false, outcome);
		}

		appointmentBookHandlerHelper.createAppointment(incomingAppointment, cancelledApptId, theRequestDetails);
		if (cancelledApptId != null) {
			appointmentBookHandlerHelper.cancelAppointment(cancelledApptId.getReference(), theRequestDetails);
		}

		return new AppointmentHandlerReturnObject(incomingAppointment, true, null);
	}

	@Async
	public CompletableFuture<AppointmentHandlerReturnObject> handleIncomingAppointmentAsync(
			String body, RequestDetails theRequestDetails) {
		AppointmentHandlerReturnObject result = handleIncomingAppointment(body, theRequestDetails);
		return CompletableFuture.completedFuture(result);
	}

	private AppointmentExtractionData extractAppointmentData(Object incomingResource) {
		if (incomingResource instanceof Appointment appointment) {
			return new AppointmentExtractionData(appointment, null, null);
		}
		if (incomingResource instanceof Parameters incomingParameters) {
			Appointment appointment = incomingParameters.getParameter().stream()
					.filter(p -> "appt-resource".equals(p.getName()))
					.findFirst()
					.map(p -> (Appointment) p.getResource())
					.orElseThrow(() -> new IllegalArgumentException(
							"Could not find an Appointment resource in incoming Parameters"));
			Reference scheduleReference = incomingParameters.hasParameter("schedule")
					? (Reference) incomingParameters.getParameter("schedule").getValue()
					: null;
			Reference cancelledApptId = incomingParameters.hasParameter("cancelled-appt-id")
					? (Reference)
							incomingParameters.getParameter("cancelled-appt-id").getValue()
					: null;
			return new AppointmentExtractionData(appointment, scheduleReference, cancelledApptId);
		}
		throw new IllegalArgumentException("Unsupported resource type in incoming body: "
				+ incomingResource.getClass().getName());
	}

	private void createSlotIfNotOverlapping(
			Appointment incomingAppointment,
			Reference scheduleReference,
			OperationOutcome outcome,
			RequestDetails requestDetails) {
		if (scheduleReference == null) {
			throw new IllegalArgumentException(
					"Slot is missing and could not find a Schedule Reference in incoming Parameters");
		} else {
			if (!appointmentBookHandlerHelper.isScheduleExistent(scheduleReference.getReference(), requestDetails)) {
				log.info("Schedule with ID : {} not found", scheduleReference);
				OperationOutcomeUtils.addIssue(
						outcome,
						"Parameters.schedule",
						MessageFormat.format(
								"Schedule with reference: {0} not found", scheduleReference.getReference()));
			}

			List<Slot> overlappingBusySlots = appointmentBookHandlerHelper.findBusyOverlappingSlots(
					incomingAppointment, scheduleReference, requestDetails);
			List<Slot> overlappingFreeSlots = appointmentBookHandlerHelper.findFreeOverlappingSlots(
					incomingAppointment, scheduleReference, requestDetails);
			if (overlappingBusySlots.isEmpty()) {
				appointmentBookHandlerHelper.createSlot(
						incomingAppointment, scheduleReference, overlappingFreeSlots, requestDetails);
			} else {
				String overlappingSlotsDetails =
						appointmentBookHandlerHelper.getOverlappingSlotDetails(overlappingBusySlots);
				log.info(
						"Incoming Appointment: Start and end are overlapping with existing slots. "
								+ "Incoming Appointment Start: {}, Incoming Appointment End: {}, Overlapping Slots: {}",
						incomingAppointment.getStart(),
						incomingAppointment.getEnd(),
						overlappingSlotsDetails);

				OperationOutcomeUtils.addIssue(
						outcome,
						"Appointment.start or Appointment.end",
						String.format(
								"Incoming Appointment: Start and end are overlapping with existing slots. "
										+ "Incoming Appointment Start: %s, Incoming Appointment End: %s, Overlapping Slots: \n%s",
								incomingAppointment.getStart(), incomingAppointment.getEnd(), overlappingSlotsDetails));
			}
		}
	}

	private OperationOutcome checkPlausibility(
			Appointment incomingAppointment, Reference cancelledApptId, RequestDetails requestDetails) {
		OperationOutcome outcome = new OperationOutcome();

		appointmentBookHandlerHelper.validateStartAndEndPresent(incomingAppointment, outcome);
		appointmentBookHandlerHelper.validateStartInFuture(incomingAppointment, outcome);
		appointmentBookHandlerHelper.validateStatusProposed(incomingAppointment, outcome);
		appointmentBookHandlerHelper.validateServiceType(incomingAppointment, outcome);

		if (appointmentBookHandlerHelper.hasSlot(incomingAppointment)) {
			String slotReference = incomingAppointment.getSlot().getFirst().getReference();
			if (!appointmentBookHandlerHelper.isSlotExistent(slotReference, requestDetails)) {
				log.info("Slot with ID : {} not found", slotReference);
				OperationOutcomeUtils.addIssue(
						outcome,
						"Appointment.slot",
						MessageFormat.format("Slot with ID: {0} not found", slotReference));
			} else {
				Slot slot = appointmentBookHandlerHelper.getSlot(slotReference, requestDetails);
				appointmentBookHandlerHelper.validateStartAndEnd(incomingAppointment, slot, outcome);
				appointmentBookHandlerHelper.validateReferencedSlotFree(slot, outcome);
			}
		}

		var patientReference =
				incomingAppointment.getParticipant().getFirst().getActor().getReference();
		Patient patient = appointmentBookHandlerHelper.getPatient(patientReference, requestDetails);
		appointmentBookHandlerHelper.validateReferencedPatientActive(patient, outcome);

		if (cancelledApptId != null
				&& !appointmentBookHandlerHelper.isCancelledAppointmentExistent(
						cancelledApptId.getReference(), requestDetails)) {
			log.info(
					"Appointment for cancellation with ID {} not found (cancelled-appt-id)",
					cancelledApptId.getReference());
			OperationOutcomeUtils.addIssue(
					outcome,
					"Parameters.cancelled-appt-id",
					MessageFormat.format(
							"Appointment for cancellation with ID {0} not found (cancelled-appt-id)",
							cancelledApptId.getReference()));
		}

		return outcome;
	}
}
