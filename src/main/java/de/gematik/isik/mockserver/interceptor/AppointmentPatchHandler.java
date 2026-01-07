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

import ca.uhn.fhir.rest.api.server.RequestDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentPatchHandler {

	@Autowired
	private final AppointmentPatchHandlerHelper appointmentPatchHandlerHelper;

	private static final String APPOINTMENT_SLOT = "Appointment.slot";
	private static final String APPOINTMENT_START = "Appointment.start";
	private static final String APPOINTMENT_END = "Appointment.end";
	private static final String APPOINTMENT_PATIENT = "Appointment.participant.actor.where(resolve() is Patient)";

	public OperationOutcome handle(Parameters parameters, RequestDetails requestDetails) {
		OperationOutcome outcome = new OperationOutcome();
		String originalAppointmentId = String.valueOf(requestDetails.getId());
		Appointment originalAppointment =
				appointmentPatchHandlerHelper.getOriginalAppointment(originalAppointmentId, requestDetails);

		appointmentPatchHandlerHelper.validateParametersOfTypeReplace(parameters, outcome);

		if (appointmentPatchHandlerHelper.isParameterPresent(parameters, APPOINTMENT_SLOT)) {
			String patchSlotId = appointmentPatchHandlerHelper.getReferenceFromParameters(parameters, APPOINTMENT_SLOT);
			appointmentPatchHandlerHelper.validateSlotReferenceUnchanged(patchSlotId, originalAppointment, outcome);
		}

		if (appointmentPatchHandlerHelper.isParameterPresent(parameters, APPOINTMENT_START)) {
			String patchStart = appointmentPatchHandlerHelper.getDateFromParameters(parameters, APPOINTMENT_START);
			appointmentPatchHandlerHelper.validateStartUnchanged(patchStart, originalAppointment, outcome);
		}

		if (appointmentPatchHandlerHelper.isParameterPresent(parameters, APPOINTMENT_END)) {
			String patchEnd = appointmentPatchHandlerHelper.getDateFromParameters(parameters, APPOINTMENT_END);
			appointmentPatchHandlerHelper.validateEndUnchanged(patchEnd, originalAppointment, outcome);
		}

		if (appointmentPatchHandlerHelper.isParameterPresent(parameters, APPOINTMENT_PATIENT)) {
			String patchPatientId =
					appointmentPatchHandlerHelper.getReferenceFromParameters(parameters, APPOINTMENT_PATIENT);
			appointmentPatchHandlerHelper.validatePatientReferenceUnchanged(
					patchPatientId, originalAppointment, outcome);
			appointmentPatchHandlerHelper.validateReferencedPatientActive(patchPatientId, outcome, requestDetails);
		}

		return outcome;
	}
}
