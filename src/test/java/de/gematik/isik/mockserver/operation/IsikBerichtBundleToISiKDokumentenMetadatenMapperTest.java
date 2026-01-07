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
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceContentComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static de.gematik.isik.mockserver.helper.ResourceLoadingHelper.loadResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class IsikBerichtBundleToISiKDokumentenMetadatenMapperTest {

	private IsikBerichtBundleToISiKDokumentenMetadatenMapper mapper;

	@Mock private IsikBerichtSubSystemeTypeMapper typeMapper;
	@Mock private IsikBerichtSubSystemeSubjectMapper subjectMapper;
	@Mock private IsiKBerichtSubSystemeRelatesToMapper relatesToMapper;
	@Mock private IsiKBerichtSubSystemeEncounterMapper encounterMapper;
	@Mock private IsiKBerichtSubSystemeContentMapper contentMapper;

	@Mock private RequestDetails requestDetails;

	private final Identifier bundleIdentifier = new Identifier().setValue("Bundle-123");
	private final String createdBundleId = "bundle-123";
	private AutoCloseable mocks;

	private final FhirContext fhirContext = FhirContext.forR4();

	@BeforeEach
	void setup() {
		mocks = MockitoAnnotations.openMocks(this);
		mapper = new IsikBerichtBundleToISiKDokumentenMetadatenMapper(
				typeMapper, subjectMapper, relatesToMapper, encounterMapper, contentMapper
		);
	}

	@AfterEach
	@SneakyThrows
	void tearDown() {
		if (mocks != null) {
			mocks.close();
		}
	}

	@Test
	void testMapCompositionToDocumentReference_WithRealBundle() {
		String body = loadResourceAsString("fhir-examples/valid/valid-generate-metadata-bundle.json");
		Bundle bundle = (Bundle) fhirContext.newJsonParser().parseResource(body);

		Composition composition = bundle.getEntry().stream()
				.filter(entry -> entry.getResource() instanceof Composition)
				.map(entry -> (Composition) entry.getResource())
				.findFirst()
				.orElseThrow(() -> new AssertionError("No Composition resource found in bundle"));

		doNothing().when(typeMapper).mapKdlAndXdsCodings(any(Composition.class), any(DocumentReference.class), any(OperationOutcome.class));

		Reference patientRef = new Reference("Patient/Real123");
		when(subjectMapper.mapSubject(eq(composition), any(OperationOutcome.class), eq(requestDetails)))
				.thenReturn(patientRef);

		when(relatesToMapper.mapRelatesToComponents(composition, requestDetails))
				.thenReturn(Collections.emptyList());

		DocumentReferenceContentComponent dummyContent = new DocumentReferenceContentComponent();
		when(contentMapper.mapContentComponent(eq(composition), any(EncodingEnum.class), eq(createdBundleId)))
				.thenReturn(dummyContent);

		doNothing().when(encounterMapper).mapEncounter(any(Composition.class), any(DocumentReference.DocumentReferenceContextComponent.class), eq(requestDetails));

		DocumentReferenceMetadataReturnObject result =
				mapper.mapCompositionToDocumentReference(composition, EncodingEnum.JSON, bundleIdentifier, createdBundleId, requestDetails);

		assertThat(result.getDocumentReference()).isNotNull();
		assertThat(result.getOperationOutcome()).isNull();

		DocumentReference docRef = result.getDocumentReference();
		assertThat(docRef.getSubject().getReference()).isEqualTo("Patient/Real123");
		assertThat(docRef.getIdentifier()).isNotEmpty();
		assertThat(docRef.getContent()).containsExactly(dummyContent);
		assertThat(docRef.getContext()).isNotNull();
	}

	@Test
	void testMapCompositionToDocumentReference_Failure() {
		Composition composition = new Composition();
		Identifier compIdentifier = new Identifier().setValue("comp-002");
		composition.setIdentifier(compIdentifier);
		composition.setStatus(Composition.CompositionStatus.FINAL);
		Reference authorRef = new Reference();
		authorRef.setDisplay("Dr. Jones");
		composition.setAuthor(List.of(authorRef));
		composition.setTitle("Failure Composition");

		doNothing().when(typeMapper).mapKdlAndXdsCodings(any(Composition.class), any(DocumentReference.class), any(OperationOutcome.class));
		// Simulate that subjectMapper returns null AND adds an error issue.
		doAnswer(invocation -> {
			OperationOutcome outcome = invocation.getArgument(1);
			outcome.addIssue()
					.setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics("No Patient resource found in the Bundle for Composition.subject");
			return null;
		}).when(subjectMapper).mapSubject(eq(composition), any(OperationOutcome.class), eq(requestDetails));

		when(relatesToMapper.mapRelatesToComponents(composition, requestDetails))
				.thenReturn(Collections.emptyList());
		DocumentReferenceContentComponent dummyContent = new DocumentReferenceContentComponent();
		when(contentMapper.mapContentComponent(eq(composition), any(EncodingEnum.class), eq(createdBundleId)))
				.thenReturn(dummyContent);
		doNothing().when(encounterMapper).mapEncounter(any(Composition.class), any(DocumentReference.DocumentReferenceContextComponent.class), eq(requestDetails));

		EncodingEnum encoding = EncodingEnum.XML;

		DocumentReferenceMetadataReturnObject result =
				mapper.mapCompositionToDocumentReference(composition, encoding, bundleIdentifier, createdBundleId, requestDetails);

		assertThat(result.getDocumentReference()).isNull();
		OperationOutcome outcome = result.getOperationOutcome();
		assertThat(outcome).isNotNull();
		assertThat(OperationOutcomeUtils.hasErrorIssue(outcome)).isTrue();
		assertThat(outcome.getIssue().get(0).getDiagnostics()).contains("No Patient resource found in the Bundle for Composition.subject");
	}
}
