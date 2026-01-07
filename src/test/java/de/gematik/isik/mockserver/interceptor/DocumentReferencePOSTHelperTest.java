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
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentReferencePOSTHelperTest {

	@Mock
	private DaoRegistry daoRegistry;

	@Mock
	private IFhirResourceDao<DocumentReference> documentReferenceDao;

	@Mock
	private RequestDetails requestDetails;

	private DocumentReferencePOSTHelper helper;

	@BeforeEach
	public void setUp() {
		helper = new DocumentReferencePOSTHelper(daoRegistry);
		when(daoRegistry.getResourceDao(DocumentReference.class)).thenReturn(documentReferenceDao);
	}

	@Test
	void processRelatesTo_validCase_updatesOriginalDocument() {
		DocumentReference docRef = new DocumentReference();
		DocumentReference.DocumentReferenceRelatesToComponent relatesTo = new DocumentReference.DocumentReferenceRelatesToComponent();
		relatesTo.setCode(DocumentReference.DocumentRelationshipType.REPLACES);
		Reference targetRef = new Reference("DocumentReference/123");
		relatesTo.setTarget(targetRef);
		docRef.addRelatesTo(relatesTo);

		DocumentReference originalDoc = new DocumentReference();
		originalDoc.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
		when(documentReferenceDao.read(new IdType("DocumentReference", "123"), requestDetails))
			.thenReturn(originalDoc);

		helper.processRelatesTo(docRef, requestDetails);

		assertThat(originalDoc.getStatus()).isEqualTo(Enumerations.DocumentReferenceStatus.SUPERSEDED);
		verify(documentReferenceDao).update(originalDoc, requestDetails);
	}

	@Test
	void processRelatesTo_missingTarget_doesNotCallDao() {
		DocumentReference docRef = new DocumentReference();
		DocumentReference.DocumentReferenceRelatesToComponent relatesTo = new DocumentReference.DocumentReferenceRelatesToComponent();
		relatesTo.setCode(DocumentReference.DocumentRelationshipType.REPLACES);
		docRef.addRelatesTo(relatesTo);

		helper.processRelatesTo(docRef, requestDetails);

		verify(documentReferenceDao, never()).read(any(IdType.class), eq(requestDetails));
		verify(documentReferenceDao, never()).update(any(DocumentReference.class), eq(requestDetails));
	}

	@Test
	void processRelatesTo_missingId_doesNotCallDao() {
		DocumentReference docRef = new DocumentReference();
		DocumentReference.DocumentReferenceRelatesToComponent relatesTo = new DocumentReference.DocumentReferenceRelatesToComponent();
		relatesTo.setCode(DocumentReference.DocumentRelationshipType.REPLACES);
		Reference targetRef = new Reference("");
		relatesTo.setTarget(targetRef);
		docRef.addRelatesTo(relatesTo);

		helper.processRelatesTo(docRef, requestDetails);

		verify(documentReferenceDao, never()).read(any(IdType.class), eq(requestDetails));
		verify(documentReferenceDao, never()).update(any(DocumentReference.class), eq(requestDetails));
	}

	@Test
	void processRelatesTo_resourceNotFound_doesNotUpdate() {
		DocumentReference docRef = new DocumentReference();
		DocumentReference.DocumentReferenceRelatesToComponent relatesTo = new DocumentReference.DocumentReferenceRelatesToComponent();
		relatesTo.setCode(DocumentReference.DocumentRelationshipType.REPLACES);
		Reference targetRef = new Reference("DocumentReference/123");
		relatesTo.setTarget(targetRef);
		docRef.addRelatesTo(relatesTo);

		when(documentReferenceDao.read(new IdType("DocumentReference", "123"), requestDetails))
			.thenThrow(new ResourceNotFoundException("Original DocumentReference not found"));

		helper.processRelatesTo(docRef, requestDetails);

		verify(documentReferenceDao, never()).update(any(DocumentReference.class), eq(requestDetails));
	}
}
