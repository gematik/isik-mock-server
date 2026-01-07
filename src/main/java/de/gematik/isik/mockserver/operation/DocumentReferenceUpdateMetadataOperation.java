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
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import de.gematik.isik.mockserver.helper.ResponseUtils;
import de.gematik.isik.mockserver.operation.DocumentReferenceOperationCommons.ParsedRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentReferenceUpdateMetadataOperation implements IResourceProvider {

	@Autowired
	private DaoRegistry daoRegistry;

	@Autowired
	private FhirContext ctx;

	@Autowired
	private DocumentReferenceUpdateMetadataHandler updateMetadataHandler;

	@Operation(name = "update-metadata", manualResponse = true, manualRequest = true)
	@SneakyThrows
	public DocumentReference updateMetadata(
			@IdParam IdType theId,
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			RequestDetails theRequestDetails) {
		ParsedRequest parsedRequest = DocumentReferenceOperationCommons.parseAndValidate(theRequest, theResponse, ctx);
		if (parsedRequest == null) {
			return null; // Error response already sent
		}
		IBaseResource incomingResource = parsedRequest.resource();
		IParser parser = parsedRequest.parser();
		EncodingEnum encoding = parsedRequest.encoding();

		if (!(incomingResource instanceof Parameters parameters)) {
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue()
					.setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics(String.format(
							"Wrong ResourceType for $update-metadata: '%s'. Must be 'Parameters'",
							incomingResource.getIdElement().getResourceType()));
			ResponseUtils.sendValidationErrorResponse(
					theResponse,
					400,
					outcome,
					String.format(
							"Wrong ResourceType for $update-metadata: '%s'. Must be 'Parameters'",
							incomingResource.getIdElement().getResourceType()),
					parser,
					encoding);
			return null;
		}

		DocumentReferenceMetadataReturnObject returnObject =
				updateMetadataHandler.handle(parameters, theId, theRequestDetails);

		if (!returnObject.isOperationSuccessful()) {
			ResponseUtils.sendValidationErrorResponse(
					theResponse, 400, returnObject.getOperationOutcome(), "Something went wrong.", parser, encoding);
			return null;
		}

		DaoMethodOutcome methodOutcomeDocRef = daoRegistry
				.getResourceDao(DocumentReference.class)
				.update(returnObject.getDocumentReference(), theRequestDetails);
		String updatedDocRef = ctx.newJsonParser().encodeResourceToString(returnObject.getDocumentReference());
		log.info(
				"Successfully updated DocumentReference with ID '{}'",
				methodOutcomeDocRef.getId().toString().replace("/_history/1", ""));
		log.debug("Response DocumentReference: {}", updatedDocRef);
		theResponse.getWriter().print(updatedDocRef);
		theResponse.setStatus(HttpServletResponse.SC_OK);

		return returnObject.getDocumentReference();
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return DocumentReference.class;
	}
}
