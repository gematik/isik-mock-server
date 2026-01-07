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
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentReferencePOSTHelper {

	private final DaoRegistry daoRegistry;

	public void processRelatesTo(DocumentReference documentReference, RequestDetails theRequestDetails) {
		IFhirResourceDao<DocumentReference> documentReferenceDao = daoRegistry.getResourceDao(DocumentReference.class);

		documentReference.getRelatesTo().stream()
				.filter(relatesTo -> relatesTo.getCode() == DocumentReference.DocumentRelationshipType.REPLACES)
				.forEach(relatesTo -> {
					Reference originalDocRef = relatesTo.getTarget();
					if (originalDocRef == null || !originalDocRef.hasReference()) {
						log.warn("Original DocumentReference reference is missing or invalid");
						return;
					}
					String originalDocId = originalDocRef.getReferenceElement().getIdPart();
					if (originalDocId == null) {
						log.warn("Original DocumentReference ID is missing");
						return;
					}
					try {
						DocumentReference originalDocument = documentReferenceDao.read(
								new IdType("DocumentReference", originalDocId), theRequestDetails);
						originalDocument.setStatus(Enumerations.DocumentReferenceStatus.SUPERSEDED);
						documentReferenceDao.update(originalDocument, theRequestDetails);
						log.info(
								"Updated status of original DocumentReference with ID {} to 'superseded'",
								originalDocId);
					} catch (ResourceNotFoundException e) {
						log.warn("Original DocumentReference with ID {} not found", originalDocId);
					}
				});
	}
}
