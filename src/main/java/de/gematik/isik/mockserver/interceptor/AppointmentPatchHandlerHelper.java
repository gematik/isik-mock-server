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

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class AppointmentPatchHandlerHelper {

	@Autowired
	private final DaoRegistry daoRegistry;

	private static final String OPERATION = "operation";

	public Appointment getOriginalAppointment(String id, RequestDetails requestDetails) {
		return daoRegistry.getResourceDao(Appointment.class).read(new IdType(id), requestDetails);
	}

	public boolean isParameterPresent(Parameters parameters, String location) {
		return parameters.getParameter().stream()
				.filter(param -> OPERATION.equals(param.getName()))
				.anyMatch(operation -> operation.getPart().stream()
						.filter(part -> "path".equals(part.getName()))
						.filter(part -> part.getValue() instanceof StringType)
						.anyMatch(part -> location.equals(((StringType) part.getValue()).getValue())));
	}

	public String getReferenceFromParameters(Parameters parameters, String location) {
		for (Parameters.ParametersParameterComponent operation : parameters.getParameter()) {
			if (isOperationForSpecifiedLocation(operation, location)) {
				return extractReference(operation, location);
			}
		}
		throw new IllegalArgumentException(String.format(
				"The '%s' parameter is missing or invalid in the provided Parameters resource.", location));
	}

	public String getDateFromParameters(Parameters parameters, String location) {
		for (Parameters.ParametersParameterComponent operation : parameters.getParameter()) {
			if (isOperationForSpecifiedLocation(operation, location)) {
				return extractDate(operation, location);
			}
		}
		throw new IllegalArgumentException(String.format(
				"The '%s' parameter is missing or invalid in the provided Parameters resource.", location));
	}

	private boolean isOperationForSpecifiedLocation(
			Parameters.ParametersParameterComponent operation, String location) {
		if (!OPERATION.equals(operation.getName())) {
			return false;
		}
		return operation.getPart().stream()
				.anyMatch(part -> "path".equals(part.getName())
						&& part.getValue() instanceof StringType stringType
						&& location.equals(stringType.getValue()));
	}

	private String extractReference(Parameters.ParametersParameterComponent operation, String location) {
		return operation.getPart().stream()
				.filter(part -> "value".equals(part.getName()) && part.getValue() instanceof Reference)
				.map(part -> ((Reference) part.getValue()).getReference())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(String.format(
						"The 'valueReference' part with a valid Reference is missing in the '%s' operation.",
						location)));
	}

	private String extractDate(Parameters.ParametersParameterComponent operation, String location) {
		return operation.getPart().stream()
				.filter(part -> "value".equals(part.getName()) && part.getValue() instanceof DateTimeType)
				.map(part -> ((DateTimeType) part.getValue()).getValueAsString())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(String.format(
						"The 'value' part with a valid DateTime is missing in the '%s' operation.", location)));
	}

	public void validateSlotReferenceUnchanged(
			String patchSlotId, Appointment originalAppointment, OperationOutcome outcome) {
		String originalSLotId = originalAppointment.getSlot().get(0).getReference();

		if (!patchSlotId.equals(originalSLotId)) {
			log.info(
					"Appointment.slot MUST NOT be changed .Original slot: {} - Patch slot: {}",
					originalSLotId,
					patchSlotId);
			OperationOutcomeUtils.addIssue(
					outcome,
					"Appointment.slot",
					String.format(
							"Appointment.slot MUST NOT be changed. Original slot: %s - Patch slot: %s",
							originalSLotId, patchSlotId));
		}
	}

	public void validatePatientReferenceUnchanged(
			String patchPatientReference, Appointment originalAppointment, OperationOutcome outcome) {
		String originalPatientReference =
				originalAppointment.getParticipant().get(0).getActor().getReference();

		if (!patchPatientReference.equals(originalPatientReference)) {
			log.info(
					"Appointment.participant.actor.where(resolve() is Patient) - The Patient Reference MUST NOT be changed. Original Patient: {} | Patch Patient: {}",
					originalPatientReference,
					patchPatientReference);
			OperationOutcomeUtils.addIssue(
					outcome,
					"Appointment.participant.actor.where(resolve() is Patient)",
					String.format(
							"The Patient Reference MUST NOT be changed. Original Patient: %s | Patch Patient: %s",
							originalPatientReference, patchPatientReference));
		}
	}

	public void validateReferencedPatientActive(
			String patchPatientId, OperationOutcome outcome, RequestDetails requestDetails) {
		Patient patient = daoRegistry.getResourceDao(Patient.class).read(new IdType(patchPatientId), requestDetails);
		if (!patient.getActive()) {
			log.info("The referenced Patient has 'active=false' but must be 'active=true'");
			OperationOutcomeUtils.addIssue(
					outcome,
					"Appointment.patient.active",
					"The referenced Patient has 'active=false' but must be 'active=true'.");
		}
	}

	public void validateStartUnchanged(String patchStart, Appointment originalAppointment, OperationOutcome outcome) {
		Date patchStartDate = Date.from(OffsetDateTime.parse(patchStart).toInstant());

		if (!originalAppointment.getStart().equals(patchStartDate)) {
			log.info(
					"Appointment.start MUST NOT be changed. Original start: {} | Patch start: {}",
					originalAppointment.getStart(),
					patchStartDate);
			OperationOutcomeUtils.addIssue(
					outcome,
					"Appointment.start",
					String.format(
							"Appointment.start MUST NOT be changed. Original start: %s | Patch start: %s",
							originalAppointment.getStart(), patchStartDate));
		}
	}

	public void validateEndUnchanged(String patchEnd, Appointment originalAppointment, OperationOutcome outcome) {
		Date patchEndDate = Date.from(OffsetDateTime.parse(patchEnd).toInstant());

		if (!originalAppointment.getEnd().equals(patchEndDate)) {
			log.info(
					"Appointment.end MUST NOT be changed. Original end: {} | Patch end: {}",
					originalAppointment.getEnd(),
					patchEndDate);
			OperationOutcomeUtils.addIssue(
					outcome,
					"Appointment.end",
					String.format(
							"Appointment.end MUST NOT be changed. Original end: %s | Patch end: %s",
							originalAppointment.getEnd(), patchEndDate));
		}
	}

	private Optional<String> extractPartValue(Parameters.ParametersParameterComponent operation, String partName) {
		return operation.getPart().stream()
				.filter(part -> partName.equals(part.getName()))
				.map(part -> {
					Object value = part.getValue();
					if (value instanceof CodeType codeType) {
						return codeType.getValue();
					} else if (value instanceof StringType stringType) {
						return stringType.getValue();
					}
					return value != null ? value.toString() : null;
				})
				.filter(Objects::nonNull)
				.findFirst();
	}

	public void validateParametersOfTypeReplace(Parameters parameters, OperationOutcome outcome) {
		for (Parameters.ParametersParameterComponent operation : parameters.getParameter()) {
			if (!OPERATION.equals(operation.getName())) {
				continue;
			}
			Optional<String> typeOpt = extractPartValue(operation, "type");
			if (typeOpt.isPresent() && !"replace".equals(typeOpt.get())) {
				String typeValue = typeOpt.get();
				String pathValue = extractPartValue(operation, "path").orElse("unknown path");
				log.info("{} - This server only supports operation type 'replace' but was '{}'", pathValue, typeValue);
				OperationOutcomeUtils.addIssue(
						outcome,
						pathValue,
						String.format("This server only supports operation type 'replace' but was '%s'", typeValue));
			}
		}
	}
}
