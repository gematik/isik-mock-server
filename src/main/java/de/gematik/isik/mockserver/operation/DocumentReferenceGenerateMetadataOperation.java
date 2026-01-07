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
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentReferenceGenerateMetadataOperation implements IResourceProvider {

	@Autowired
	private DaoRegistry daoRegistry;

	@Autowired
	private FhirContext ctx;

	@Autowired
	private IsikBerichtBundleToISiKDokumentenMetadatenMapper bundleToISiKDokumentenMetadatenMapper;

	private static final String ISIK_BERICHT_BUNDLE_PROFILE =
			"https://gematik.de/fhir/isik/v3/Basismodul/StructureDefinition/ISiKBerichtBundle";
	private static final String ISIK_BERICHT_SUBSYSTEME_PROFILE =
			"https://gematik.de/fhir/isik/v3/Basismodul/StructureDefinition/ISiKBerichtSubSysteme";

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return DocumentReference.class;
	}

	@Operation(name = "generate-metadata", manualResponse = true, manualRequest = true)
	@SneakyThrows
	public DocumentReference generateMetadata(
			HttpServletRequest theRequest, HttpServletResponse theResponse, RequestDetails theRequestDetails) {
		ParsedRequest parsedRequest = DocumentReferenceOperationCommons.parseAndValidate(theRequest, theResponse, ctx);
		if (parsedRequest == null) {
			return null; // Error response is already sent.
		}
		IBaseResource incomingResource = parsedRequest.resource();
		IParser parser = parsedRequest.parser();
		EncodingEnum encoding = parsedRequest.encoding();

		if (!isIsikBerichtBundle(incomingResource)) {
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue()
					.setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics(
							String.format("Unsupported Document Type - must be %s", ISIK_BERICHT_BUNDLE_PROFILE));
			ResponseUtils.sendValidationErrorResponse(
					theResponse,
					400,
					outcome,
					String.format("Unsupported Document Type - must be %s", ISIK_BERICHT_BUNDLE_PROFILE),
					parser,
					encoding);
			return null;
		}

		Bundle incomingBundle = (Bundle) incomingResource;

		Optional<Composition> compositionOptional = extractCompositionFromBundle(incomingBundle);
		if (compositionOptional.isEmpty()) {
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue()
					.setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics(String.format(
							"Couldn't find correct Composition in Bundle: %s", ISIK_BERICHT_SUBSYSTEME_PROFILE));
			ResponseUtils.sendValidationErrorResponse(
					theResponse,
					400,
					outcome,
					String.format("Couldn't find correct Composition in Bundle: %s", ISIK_BERICHT_SUBSYSTEME_PROFILE),
					parser,
					encoding);
			return null;
		}

		DaoMethodOutcome methodOutcomeBundle =
				daoRegistry.getResourceDao(Bundle.class).create(incomingBundle, theRequestDetails);
		String createdBundleId = methodOutcomeBundle.getId().toString().replace("/_history/1", "");
		DocumentReferenceMetadataReturnObject returnObject =
				bundleToISiKDokumentenMetadatenMapper.mapCompositionToDocumentReference(
						compositionOptional.get(),
						encoding,
						incomingBundle.getIdentifier(),
						createdBundleId,
						theRequestDetails);

		if (!returnObject.isOperationSuccessful()) {
			daoRegistry.getResourceDao(Bundle.class).delete(new IdType(createdBundleId), theRequestDetails);
			ResponseUtils.sendValidationErrorResponse(
					theResponse, 400, returnObject.getOperationOutcome(), "Something went wrong.", parser, encoding);
			return null;
		}

		DaoMethodOutcome methodOutcomeDocRef = daoRegistry
				.getResourceDao(DocumentReference.class)
				.create(returnObject.getDocumentReference(), theRequestDetails);
		String createdDocRef = ctx.newJsonParser().encodeResourceToString(returnObject.getDocumentReference());
		log.info(
				"Successfully created Bundle with ID '{}' and DocumentReference with ID '{}'",
				createdBundleId,
				methodOutcomeDocRef.getId().toString().replace("/_history/1", ""));
		log.debug("Response DocumentReference: {}", createdDocRef);
		theResponse.getWriter().print(createdDocRef);
		theResponse.setStatus(HttpServletResponse.SC_CREATED);

		return returnObject.getDocumentReference();
	}

	private boolean isIsikBerichtBundle(IBaseResource resource) {
		return (resource instanceof Bundle)
				&& resource.getMeta().getProfile().stream()
						.map(IPrimitiveType::getValueAsString)
						.anyMatch(ISIK_BERICHT_BUNDLE_PROFILE::equals);
	}

	private Optional<Composition> extractCompositionFromBundle(Bundle bundle) {
		return bundle.getEntry().stream()
				.map(Bundle.BundleEntryComponent::getResource)
				.filter(Composition.class::isInstance)
				.map(Composition.class::cast)
				.filter(composition -> composition.getMeta() != null
						&& composition.getMeta().getProfile().stream()
								.map(PrimitiveType::getValueAsString)
								.anyMatch(ISIK_BERICHT_SUBSYSTEME_PROFILE::equals))
				.findFirst();
	}
}
