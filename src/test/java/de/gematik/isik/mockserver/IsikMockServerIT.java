package de.gematik.isik.mockserver;

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
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.r4.model.Account;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Appointment.AppointmentStatus;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ValueSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static de.gematik.isik.mockserver.helper.ResourceLoadingHelper.loadResourceAsString;
import static de.gematik.isik.mockserver.provider.DocumentReferenceStufe3ResourceProviderHelper.XDS_CLASS_CODE_SYSTEM;
import static de.gematik.isik.mockserver.provider.DocumentReferenceStufe3ResourceProviderHelper.XDS_TYPE_CODE_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class)
@ActiveProfiles("integrationtest")
@Slf4j
class IsikMockServerIT {

    public static final int ONE_MINUTE = 60 * 1000;
    private IGenericClient ourClient;
    private static FhirContext ourCtx;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void beforeAll() {
        ourCtx = FhirContext.forR4();
        ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        ourCtx.getRestfulClientFactory().setSocketTimeout(ONE_MINUTE);
    }

    @BeforeEach
    void beforeEach() {
        ourClient = ourCtx.newRestfulGenericClient(getServerHostAndPort());
        ourClient.registerInterceptor(new BearerTokenAuthInterceptor("bearerToken"));
    }

    @NotNull
    private String getServerHostAndPort() {
        return String.format("http://localhost:%s/fhir/", port);
    }

    @SneakyThrows
    @Test
    void correctAppointmentCanBeBooked() {
        String body = loadResourceAsString("fhir-examples/valid/valid-appointment.json");
        Appointment termin = (Appointment) ourCtx.newJsonParser().parseResource(body);

        final var response = restTemplate.execute(
                getServerHostAndPort() + "/Appointment/$book", HttpMethod.POST, request -> {
                    ourCtx.newJsonParser().encodeResourceToWriter(termin, new OutputStreamWriter(request.getBody()));
                    request.getHeaders().add("Content-Type", "application/fhir+json");
                    request.getHeaders().add("Accept", "application/fhir+json");
                }, response1 -> (Appointment)ourCtx.newJsonParser().parseResource(response1.getBody()));

        assertThat(response.getId()).isNotNull();
    }

    @SneakyThrows
    @Test
    void appointmentCanBeBookedWithAParametersRequestAndScheduleReference() {
        String body = loadResourceAsString("fhir-examples/valid/valid-appointment-booking-parameters.json");
        Parameters parameters = (Parameters) ourCtx.newJsonParser().parseResource(body);

        final var response = restTemplate.execute(
                getServerHostAndPort() + "/Appointment/$book", HttpMethod.POST, request -> {
                    ourCtx.newJsonParser().encodeResourceToWriter(parameters, new OutputStreamWriter(request.getBody()));
                    request.getHeaders().add("Content-Type", "application/fhir+json");
                    request.getHeaders().add("Accept", "application/fhir+json");
                }, response1 -> (Appointment) ourCtx.newJsonParser().parseResource(response1.getBody()));

        assertThat(response.getId()).isNotNull();
        assertThat(response.getSlot()).isNotEmpty();
    }

    @SneakyThrows
    @Test
    void incompleteAppointmentsAreDeclined() {
        final var termin = new Appointment();
        termin.setStatus(AppointmentStatus.PROPOSED);

        final var response = restTemplate.execute(
                getServerHostAndPort() + "/Appointment/$book", HttpMethod.POST, request -> {
                    ourCtx.newJsonParser().encodeResourceToWriter(termin, new OutputStreamWriter(request.getBody()));
                    request.getHeaders().add("Content-Type", "application/fhir+json");
                }, response1 -> {
                    assertEquals(400, response1.getStatusCode().value());
                    return new String(response1.getBody().readAllBytes(), StandardCharsets.UTF_8);
                });

        log.info(response);
        var encoding = EncodingEnum.detectEncodingNoDefault(response);
        assertNotNull(encoding, "Unknown encoding of response: " + response);

        var outcome = (OperationOutcome) encoding.newParser(ourCtx).parseResource(response);
        assertTrue(outcome.getIssue().stream().anyMatch(i -> i.getSeverity() == IssueSeverity.ERROR), "OperationOutcome contains no errors");
    }

    private DocumentReference getTestDocumentReference() throws IOException {
        try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream("fhir-examples/valid/DocumentReference-Valid-Example.json")) {
            return (DocumentReference) ourCtx.newJsonParser().parseResource(inputStream);
        }
    }

    @Test
    @SneakyThrows
    void documentReferenceWithAttachmentCanBeSubmitted() {
        final var responseResource = restTemplate.execute(
                getServerHostAndPort() + "/DocumentReference", HttpMethod.POST, request -> {

                    try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream("fhir-examples/valid/DocumentReference-Valid-Example.json")) {
                        if(inputStream == null)
                            throw new IllegalStateException("Test resource not found");

                        inputStream.transferTo(request.getBody());
                    }
                    request.getHeaders().add("Content-Type", "application/fhir+json");
                    request.getHeaders().add("Accept", "application/fhir+json");
                }, response1 -> ourCtx.newJsonParser().parseResource(response1.getBody()));

        if(responseResource instanceof OperationOutcome oo) {
            fail("OperationOutcome returned: " + ourCtx.newJsonParser().encodeResourceToString(oo));
            return;
        }

        var response = (DocumentReference) responseResource;
        assertNotNull(response.getId(), "No DocumentReference id returned");
        assertNull(response.getContent().get(0).getAttachment().getData(), "Attachment base64 data was returned");
        assertTrue(response.getType().getCoding().stream().anyMatch(c -> c.getSystem().equals(XDS_TYPE_CODE_SYSTEM)), "XDS code of type is missing");
        assertFalse(response.getCategory().isEmpty(), "No vategory element in the resource");
        assertTrue(response.getCategory().get(0).getCoding().stream().anyMatch(c -> c.getSystem().equals(XDS_CLASS_CODE_SYSTEM)), "XDS code of category is missing");

        // Assert binary has been persisted correctly
        String binaryUrl = response.getContent().get(0).getAttachment().getUrl();
        assertNotNull(binaryUrl, "No URL assigned to the binary content of the published document");

        final var binaryResponse = restTemplate.execute(
                getServerHostAndPort() + binaryUrl, HttpMethod.GET, request -> request.getHeaders().add("Accept", "application/pdf"), response1 -> new String(Base64.encodeBase64(response1.getBody().readAllBytes()), Constants.CHARSET_UTF8));
        assertEquals("base64encodedPDFdata", binaryResponse, "Binary resource stored incorrectly");
    }

    @Test
    @SneakyThrows
    void documentReferenceWithMissingAttachmentDataProduces422Response() {
        final var response = restTemplate.execute(
                getServerHostAndPort() + "/DocumentReference", HttpMethod.POST, request -> {

                    var documentReference = getTestDocumentReference();
                    documentReference.getContent().get(0).getAttachment().setData(null);
                    ourCtx.newJsonParser().encodeResourceToWriter(documentReference, new OutputStreamWriter(request.getBody()));

                    request.getHeaders().add("Content-Type", "application/fhir+json");
                    request.getHeaders().add("Accept", "application/fhir+json");
                }, response1 -> {
                    assertEquals(422, response1.getStatusCode().value());
                    return new String(response1.getBody().readAllBytes(), StandardCharsets.UTF_8);
                });

        log.info(response);
        var encoding = EncodingEnum.detectEncodingNoDefault(response);
        assertNotNull(encoding, "Unknown encoding of response: " + response);

        var outcome = (OperationOutcome) encoding.newParser(ourCtx).parseResource(response);
        assertTrue(outcome.getIssue().stream().anyMatch(i -> i.getSeverity() == IssueSeverity.ERROR), "OperationOutcome contains no errors");
    }

    @Test
    @SneakyThrows
    void documentReferenceWithMissingPatientProduces422Response() {
        final var response = restTemplate.execute(
                getServerHostAndPort() + "/DocumentReference", HttpMethod.POST, request -> {

                    var documentReference = getTestDocumentReference();
                    documentReference.setSubject(new Reference("Patient/Non-Existing-Patient"));
                    ourCtx.newJsonParser().encodeResourceToWriter(documentReference, new OutputStreamWriter(request.getBody()));

                    request.getHeaders().add("Content-Type", "application/fhir+json");
                    request.getHeaders().add("Accept", "application/fhir+json");
                }, response1 -> {
                    assertEquals(422, response1.getStatusCode().value());
                    return new String(response1.getBody().readAllBytes(), StandardCharsets.UTF_8);
                });

        log.info(response);
        var encoding = EncodingEnum.detectEncodingNoDefault(response);
        assertNotNull(encoding, "Unknown encoding of response: " + response);

        var outcome = (OperationOutcome) encoding.newParser(ourCtx).parseResource(response);
        assertTrue(outcome.getIssue().stream().anyMatch(i -> i.getSeverity() == IssueSeverity.ERROR), "OperationOutcome contains no errors");
    }

    @Test
    @SneakyThrows
    void documentReferenceWithMissingEncounterProduces400Response() {
        final var response = restTemplate.execute(
                getServerHostAndPort() + "/DocumentReference", HttpMethod.POST, request -> {

                    var documentReference = getTestDocumentReference();
                    documentReference.getContext().setEncounter(List.of(new Reference("Encounter/Non-Existing-Encounter")));
                    ourCtx.newJsonParser().encodeResourceToWriter(documentReference, new OutputStreamWriter(request.getBody()));

                    request.getHeaders().add("Content-Type", "application/fhir+json");
                    request.getHeaders().add("Accept", "application/fhir+json");
                }, response1 -> {
                    assertEquals(422, response1.getStatusCode().value());
                    return new String(response1.getBody().readAllBytes(), StandardCharsets.UTF_8);
                });

        log.info(response);
        var encoding = EncodingEnum.detectEncodingNoDefault(response);
        assertNotNull(encoding, "Unknown encoding of response: " + response);

        var outcome = (OperationOutcome) encoding.newParser(ourCtx).parseResource(response);
        assertTrue(outcome.getIssue().stream().anyMatch(i -> i.getSeverity() == IssueSeverity.ERROR), "OperationOutcome contains no errors");
    }

    @Test
    void documentReferenceWithValidRelatesToReplacesRelationSetsStatusOfOriginalDocumentReferenceToSuperseded() {
        IParser parser = ourCtx.newJsonParser();
        String originalDocBody = loadResourceAsString("original-DocumentReference.json");
        String url = String.format("%s/DocumentReference", String.format("http://localhost:%s/fhir/", port));

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/fhir+json");
        headers.add("Accept", "application/fhir+json");
        HttpEntity<String> requestEntity = new HttpEntity<>(originalDocBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class
        );
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        DocumentReference originalDocumentReference = (DocumentReference) parser.parseResource(response.getBody());
        assertThat(originalDocumentReference.getStatus()).isEqualTo(Enumerations.DocumentReferenceStatus.CURRENT);

        String updatedDocBody = loadResourceAsString("fhir-examples/valid/updated-DocumentReference.json");

        // Note:
        // "By default, HAPI will strip resource versions from references between resources.
        // For example, if you set a reference to Patient.managingOrganization to the value Patient/123/_history/2,
        // HAPI will encode this reference as Patient/123"
        // Source: https://hapifhir.io/hapi-fhir/docs/model/references.html#versioned-references
        String originalDocId = originalDocumentReference.getId().replace("/_history/1", "");
        updatedDocBody = updatedDocBody.replace("DocumentReference/DOCUMENT_REPLACES_ID", originalDocId);
        HttpEntity<String> requestEntity1 = new HttpEntity<>(updatedDocBody, headers);

        ResponseEntity<String> response1 = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity1,
                String.class
        );
        assertThat(response1.getStatusCode().value()).isEqualTo(201);

        HttpEntity<String> requestEntity2 = new HttpEntity<>("", headers);
        String url2 = String.format("%s/%s", String.format("http://localhost:%s/fhir/", port), originalDocId);
        ResponseEntity<String> response2 = restTemplate.exchange(
                url2,
                HttpMethod.GET,
                requestEntity2,
                String.class
        );

        assertThat(response2.getStatusCode().value()).isEqualTo(200);
        DocumentReference updatedOriginalDocRef = (DocumentReference) parser.parseResource(response2.getBody());
        assertThat(updatedOriginalDocRef.getStatus()).isEqualTo(Enumerations.DocumentReferenceStatus.SUPERSEDED);
    }

    @SneakyThrows
    @Test
    void searchEncounterByAccountIdentifier() {
        String body = loadResourceAsString("fhir-examples/valid/valid-account.json");
        Account account = (Account) ourCtx.newJsonParser().parseResource(body);
        var createdAccount = ourClient.create().resource(account).execute();

        String body1 = loadResourceAsString("fhir-examples/valid/valid-encounter.json");
        Encounter testEncounter = (Encounter) ourCtx.newJsonParser().parseResource(body1);
        testEncounter.getAccount().get(0).setReference(String.valueOf(createdAccount.getId().toUnqualified()));

        var createdEncounter = ourClient.create().resource(testEncounter).execute();

        try {
            String theSearchUrl = "Encounter?account:identifier=http%3A%2F%2Ffoo%2Facc%7C12345";
            final var encounterBundle = ourClient.search().byUrl(theSearchUrl).returnBundle(Bundle.class)
                    .execute();
            var encounter = (Encounter) encounterBundle
                    .getEntry()
                    .get(0)
                    .getResource();

            assertEquals(1, encounterBundle.getEntry().size());
            assertEquals(encounter.getId(), createdEncounter.getId().toString(), "Expected Encounter not found");
        } finally {
            ourClient.delete().resource(createdEncounter.getResource()).execute();
            ourClient.delete().resource(createdAccount.getResource()).execute();
        }
    }

    @SneakyThrows
    @Test
    void searchEncounterByPatientIdentifier() {
        String body = loadResourceAsString("fhir-examples/valid/valid-patient.json");
        Patient patient = (Patient) ourCtx.newJsonParser().parseResource(body);
        var createdPatient = ourClient.create().resource(patient).execute();

        String body1 = loadResourceAsString("fhir-examples/valid/valid-encounter.json");
        Encounter testEncounter = (Encounter) ourCtx.newJsonParser().parseResource(body1);
        testEncounter.setSubject(new Reference(createdPatient.getId().toUnqualified()));
        var createdEncounter = ourClient.create().resource(testEncounter).execute();

        try {
            String theSearchUrl = "Encounter?patient:identifier=http%3A%2F%2Ffoo%2Fpat%7C12345";
            final var encounterBundle = ourClient.search().byUrl(theSearchUrl).returnBundle(Bundle.class)
                    .execute();

            assertEquals(1, encounterBundle.getEntry().size());

            var encounter = (Encounter) encounterBundle
                    .getEntry()
                    .get(0)
                    .getResource();

            assertEquals(encounter.getId(), createdEncounter.getId().toString(), "Expected Encounter not found");
        } finally {
            ourClient.delete().resource(createdEncounter.getResource()).execute();
            ourClient.delete().resource(createdPatient.getResource()).execute();
        }
    }

    @SneakyThrows
    @Test
    void searchValueSetByContextTypeValue() {
        String body = loadResourceAsString("fhir-examples/valid/valid-valueset.json");
        ValueSet valueSet = (ValueSet) ourCtx.newJsonParser().parseResource(body);
        var createdValueSet = ourClient.create().resource(valueSet).execute();

        String theSearchUrl = "ValueSet?context-type-value=focus%24http%3A%2F%2Fhl7.org%2Ffhir%2Fresource-types%7CEncounter";
        final var results = ourClient.search().byUrl(theSearchUrl).returnBundle(Bundle.class)
                .execute();
        assertFalse(results.getEntry().isEmpty(), "No search results returned");
        var idFound = results.getEntry().stream().anyMatch(e -> e.getResource().getId().equals(createdValueSet.getId().toString()));
        assertTrue(idFound, "Created value set not found");
    }
}
