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
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentReferenceUpdateMetadataHandler {

	@Autowired
	private final DaoRegistry daoRegistry;

	public DocumentReferenceMetadataReturnObject handle(
			Parameters parameters, IdType id, RequestDetails requestDetails) {
		OperationOutcome outcome = new OperationOutcome();

		DocumentReference documentReference = fetchDocumentReference(id, requestDetails, outcome);
		if (documentReference == null) {
			return new DocumentReferenceMetadataReturnObject(null, false, outcome);
		}

		String docStatusParam = extractDocStatusParameter(parameters, outcome);
		if (docStatusParam == null) {
			return new DocumentReferenceMetadataReturnObject(null, false, outcome);
		}

		updateDocumentReference(documentReference, docStatusParam, outcome);

		return !OperationOutcomeUtils.hasErrorIssue(outcome)
				? new DocumentReferenceMetadataReturnObject(documentReference, true, null)
				: new DocumentReferenceMetadataReturnObject(null, false, outcome);
	}

	private DocumentReference fetchDocumentReference(
			IdType id, RequestDetails requestDetails, OperationOutcome outcome) {
		try {
			return daoRegistry.getResourceDao(DocumentReference.class).read(id, requestDetails);
		} catch (ResourceNotFoundException e) {
			String message = "DocumentReference with id " + id.getValue() + " not found.";
			log.info(message);
			outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(message);
			return null;
		}
	}

	private String extractDocStatusParameter(Parameters parameters, OperationOutcome outcome) {
		String docStatusParam = null;
		for (Parameters.ParametersParameterComponent param : parameters.getParameter()) {
			if ("docStatus".equals(param.getName())) {
				docStatusParam = param.getValue().toString();
			} else {
				String message = String.format("Unsupported parameter: '%s'", param.getName());
				outcome.addIssue()
						.setSeverity(OperationOutcome.IssueSeverity.ERROR)
						.setDiagnostics(message);
				log.info(message);
			}
		}
		if (docStatusParam == null) {
			String message = "Missing required parameter: 'docStatus'";
			log.info(message);
			outcome.addIssue().setDiagnostics(message).setSeverity(OperationOutcome.IssueSeverity.ERROR);
		}
		return docStatusParam;
	}

	private void updateDocumentReference(
			DocumentReference documentReference, String docStatusParam, OperationOutcome outcome) {
		try {
			DocumentReference.ReferredDocumentStatus referredStatus =
					DocumentReference.ReferredDocumentStatus.fromCode(docStatusParam);
			documentReference.setDocStatus(referredStatus);

			// Prepare the infoText message with timestamp.
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			String currentDateTime = LocalDateTime.now().format(formatter);
			Narrative narrative = getNarrative(documentReference, referredStatus, currentDateTime);
			documentReference.setText(narrative);
		} catch (FHIRException e) {
			String message = String.format("Invalid docStatus value: '%s'", docStatusParam);
			log.error(message, e);
			outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(message);
		}
	}

	@NotNull
	private static Narrative getNarrative(
			DocumentReference documentReference,
			DocumentReference.ReferredDocumentStatus referredStatus,
			String currentDateTime) {
		String infoText = String.format(
				"<p>DocumentReference.docStatus updated to: '%s' at %s</p></div>",
				referredStatus.toCode(), currentDateTime);

		Narrative existingNarrative = documentReference.getText();
		String updatedText;
		if (existingNarrative != null
				&& existingNarrative.getDivAsString() != null
				&& !existingNarrative.getDivAsString().isEmpty()) {
			updatedText = existingNarrative.getDivAsString().replace("</div>", "") + infoText;
		} else {
			updatedText = "<div>" + infoText;
		}

		Narrative narrative = new Narrative();
		narrative.setDivAsString(updatedText);
		return narrative;
	}
}
