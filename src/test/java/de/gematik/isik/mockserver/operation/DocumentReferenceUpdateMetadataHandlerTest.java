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
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DocumentReferenceUpdateMetadataHandlerTest {

	private DocumentReferenceUpdateMetadataHandler handler;

	@Mock
	private DaoRegistry daoRegistry;

	@Mock
	private IFhirResourceDao<DocumentReference> documentReferenceDao;

	@Mock
	private RequestDetails requestDetails;

	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		handler = new DocumentReferenceUpdateMetadataHandler(daoRegistry);
	}

	@AfterEach
	@SneakyThrows
	void tearDown() {
		if (mocks != null) {
			mocks.close();
		}
	}

	@Test
	void testHandle_ValidDocStatus() {
		IdType id = new IdType("DocumentReference", "1");
		Parameters parameters = new Parameters();
		parameters.addParameter()
			.setName("docStatus")
			.setValue(new StringType("final"));

		DocumentReference docRef = new DocumentReference();
		docRef.setDocStatus(DocumentReference.ReferredDocumentStatus.PRELIMINARY);
		docRef.setText(new Narrative());
		when(daoRegistry.getResourceDao(DocumentReference.class)).thenReturn(documentReferenceDao);
		when(documentReferenceDao.read(id, requestDetails))
			.thenReturn(docRef);

		DocumentReferenceMetadataReturnObject result = handler.handle(parameters, id, requestDetails);

		assertThat(result.isOperationSuccessful()).isTrue();
		assertThat(result.getDocumentReference()).isNotNull();
		assertThat(result.getOperationOutcome()).isNull();

		assertThat(docRef.getDocStatus()).isNotNull();
		assertThat(docRef.getText()).isNotNull();
		assertThat(docRef.getText().getDivAsString())
			.contains("DocumentReference.docStatus updated to: 'final'");
	}

	@Test
	void testHandle_DocumentNotFound() {
		IdType id = new IdType("DocumentReference", "1");
		Parameters parameters = new Parameters();
		parameters.addParameter()
			.setName("docStatus")
			.setValue(new StringType("current"));

		when(daoRegistry.getResourceDao(DocumentReference.class)).thenReturn(documentReferenceDao);
		when(documentReferenceDao.read(id, requestDetails))
			.thenThrow(new ResourceNotFoundException("DocumentReference with id " + id.getValue() + " not found."));

		DocumentReferenceMetadataReturnObject result = handler.handle(parameters, id, requestDetails);

		assertThat(result.isOperationSuccessful()).isFalse();
		assertThat(result.getDocumentReference()).isNull();
		OperationOutcome outcome = result.getOperationOutcome();
		assertThat(outcome).isNotNull();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
	}

	@Test
	void testHandle_MissingDocStatusParameter() {
		IdType id = new IdType("DocumentReference", "1");
		Parameters parameters = new Parameters();

		DocumentReference docRef = new DocumentReference();
		when(daoRegistry.getResourceDao(DocumentReference.class)).thenReturn(documentReferenceDao);
		when(documentReferenceDao.read(id, requestDetails))
			.thenReturn(docRef);

		DocumentReferenceMetadataReturnObject result = handler.handle(parameters, id, requestDetails);

		assertThat(result.isOperationSuccessful()).isFalse();
		assertThat(result.getDocumentReference()).isNull();
		OperationOutcome outcome = result.getOperationOutcome();
		assertThat(outcome).isNotNull();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
			.anyMatch(issue -> issue.getDiagnostics().contains("Missing required parameter: 'docStatus'"));
	}

	@Test
	void testHandle_InvalidDocStatusValue() {
		IdType id = new IdType("DocumentReference", "1");
		Parameters parameters = new Parameters();
		parameters.addParameter()
			.setName("docStatus")
			.setValue(new StringType("invalid_value"));

		DocumentReference docRef = new DocumentReference();
		when(daoRegistry.getResourceDao(DocumentReference.class)).thenReturn(documentReferenceDao);
		when(documentReferenceDao.read(id, requestDetails))
			.thenReturn(docRef);

		DocumentReferenceMetadataReturnObject result = handler.handle(parameters, id, requestDetails);

		assertThat(result.isOperationSuccessful()).isFalse();
		assertThat(result.getDocumentReference()).isNull();
		OperationOutcome outcome = result.getOperationOutcome();
		assertThat(outcome).isNotNull();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
			.anyMatch(issue -> issue.getDiagnostics().contains("Invalid docStatus value: 'invalid_value'"));
	}

	@Test
	void testHandle_InvalidDocStatusValue_WithExtraParameter() {
		IdType id = new IdType("DocumentReference", "1");
		Parameters parameters = new Parameters();
		parameters.addParameter()
			.setName("docStatus")
			.setValue(new StringType("invalid_value"));
		parameters.addParameter()
			.setName("unsupported")
			.setValue(new StringType("someValue"));

		DocumentReference docRef = new DocumentReference();
		when(daoRegistry.getResourceDao(DocumentReference.class)).thenReturn(documentReferenceDao);
		when(documentReferenceDao.read(id, requestDetails))
			.thenReturn(docRef);

		DocumentReferenceMetadataReturnObject result = handler.handle(parameters, id, requestDetails);

		assertThat(result.isOperationSuccessful()).isFalse();
		assertThat(result.getDocumentReference()).isNull();
		OperationOutcome outcome = result.getOperationOutcome();
		assertThat(outcome).isNotNull();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue())
			.anyMatch(issue -> issue.getDiagnostics().contains("Invalid docStatus value: 'invalid_value'"))
			.anyMatch(issue -> issue.getDiagnostics().contains("Unsupported parameter: 'unsupported'"));
	}
}
