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
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class IsikBerichtSubSystemeSubjectMapper {

	@Autowired
	private final DaoRegistry daoRegistry;

	private static final String SYSTEM = "system";
	private static final String CODE = "code";

	public Reference mapSubject(
			Composition composition, OperationOutcome operationOutcome, RequestDetails requestDetails) {
		Patient patientInBundle = (Patient) composition.getSubject().getResource();
		if (patientInBundle == null) {
			log.info("No Patient resource found in the Bundle for Composition.subject");
			operationOutcome
					.addIssue()
					.setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics("No Patient resource found in the Bundle for Composition.subject");
			return null;
		}

		List<Map<String, String>> identifierTypes = List.of(
				Map.of(SYSTEM, "http://terminology.hl7.org/CodeSystem/v2-0203", CODE, "MR"),
				Map.of(SYSTEM, "http://fhir.de/CodeSystem/identifier-type-de-basis", CODE, "GKV"),
				Map.of(SYSTEM, "http://fhir.de/CodeSystem/identifier-type-de-basis", CODE, "PKV"));

		Optional<Identifier> identifierOpt = identifierTypes.stream()
				.flatMap(type -> patientInBundle.getIdentifier().stream().filter(id -> id.getType().getCoding().stream()
						.anyMatch(coding -> type.get(CODE).equals(coding.getCode())
								&& type.get(SYSTEM).equals(coding.getSystem()))))
				.findFirst();

		if (identifierOpt.isEmpty()) {
			log.info("No PID, GKV, or PKV identifier found in the Patient resource referenced by Composition.subject");
			operationOutcome
					.addIssue()
					.setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics(
							"No PID, GKV, or PKV identifier found in the Patient resource referenced by Composition.subject");
			return null;
		}

		Identifier identifier = identifierOpt.get();
		String system = identifier.getSystem();
		String value = identifier.getValue();
		SearchParameterMap params = new SearchParameterMap();
		params.add("identifier", new TokenParam(system, value));
		IBundleProvider searchResult = daoRegistry.getResourceDao(Patient.class).search(params, requestDetails);

		// Fetch up to 2 resources since we expect only 1
		List<Patient> patients = searchResult.getResources(0, 2).stream()
				.map(Patient.class::cast)
				.toList();

		if (patients.size() != 1) {
			log.info("Invalid number of Patients found on server with identifier: "
					+ system
					+ "|"
					+ value
					+ ". Expected 1, found "
					+ patients.size());
			operationOutcome
					.addIssue()
					.setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics("Invalid number of Patients found on server with identifier: "
							+ system
							+ "|"
							+ value
							+ ". Expected 1, found "
							+ patients.size());
			return null;
		}

		Patient serverPatient = patients.get(0);
		return new Reference(serverPatient);
	}
}
