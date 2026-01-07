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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;

@Component
@Slf4j
public class CompositionBundleHandler {

	@Autowired
	IFhirResourceDao<Patient> patientDao;

	@Autowired
	IFhirResourceDao<Encounter> encounterDao;

	private final FhirContext ctx = FhirContext.forCached(FhirVersionEnum.R4);

	@Value("${server.port}")
	private int serverPort = 9032;

	public boolean isDocBundle(final String request) {

		final IParser parser = xmlOrJson(request);
		final Resource iBaseResource = (Resource) parser.parseResource(request);
		if (iBaseResource.getResourceType() != ResourceType.Bundle) {
			return false;
		}
		return (((Bundle) iBaseResource).getType() == Bundle.BundleType.DOCUMENT);
	}

	public CompositionHandlerReturnObject handleDocBundle(final String request) {
		final IParser parser = xmlOrJson(request);
		final Resource iBaseResource = (Resource) parser.parseResource(request);
		if (iBaseResource.getResourceType() != ResourceType.Bundle) {
			throw new PreconditionFailedException("Resource was not of type Bundle");
		}
		if (((Bundle) iBaseResource).getType() != Bundle.BundleType.DOCUMENT) {
			throw new PreconditionFailedException("Bundle was not a document Bundle");
		}
		final Bundle docBundle = (Bundle) iBaseResource;
		final Composition composition = (Composition) getResourceTypeFromBundle(docBundle, ResourceType.Composition);
		final Patient patient = (Patient) getResourceTypeFromBundle(docBundle, ResourceType.Patient);
		final Identifier patIdentifier = patient.getIdentifierFirstRep();
		final Encounter encounter = (Encounter) getResourceTypeFromBundle(docBundle, ResourceType.Encounter);
		final Identifier encounterIdentifier = encounter.getIdentifierFirstRep();
		final CompositionHandlerReturnObject returnObject = new CompositionHandlerReturnObject();
		final OperationOutcome operationOutcome = new OperationOutcome();

		if (composition.getText() == null || composition.getText().isEmpty()) {
			returnObject.setOperationSuccessful(false);
			operationOutcome
					.addIssue()
					.addLocation("Composition.text")
					.setSeverity(IssueSeverity.ERROR)
					.setCode(IssueType.PROCESSING)
					.setDiagnostics("Composition has no Narrative");
		}

		if (!isSubjectExistent(patIdentifier)) {
			returnObject.setOperationSuccessful(false);
			operationOutcome
					.addIssue()
					.addLocation("Composition.subject.identifier")
					.setSeverity(IssueSeverity.ERROR)
					.setCode(IssueType.PROCESSING)
					.setDiagnostics(MessageFormat.format(
							"Subject with identifier : {0}|{1} not found",
							patIdentifier.getSystem(), patIdentifier.getValue()));
		}
		if (!isEncounterExistent(encounterIdentifier)) {
			returnObject.setOperationSuccessful(false);
			operationOutcome
					.addIssue()
					.addLocation("Composition.encounter.identifier")
					.setSeverity(IssueSeverity.ERROR)
					.setCode(IssueType.PROCESSING)
					.setDiagnostics(MessageFormat.format(
							"Encounter with identifier : {0}|{1} not found",
							encounterIdentifier.getSystem(), encounterIdentifier.getValue()));
		}
		if (returnObject.isOperationSuccessful()) {
			operationOutcome
					.addIssue()
					.setCode(IssueType.INFORMATIONAL)
					.setSeverity(IssueSeverity.INFORMATION)
					.setDiagnostics("No issues detected");
		}
		returnObject.setOperationOutcome(ctx.newJsonParser().encodeResourceToString(operationOutcome));
		return returnObject;
	}

	private Resource getResourceTypeFromBundle(final Bundle docBundle, final ResourceType t) {
		final List<Resource> collect = docBundle.getEntry().stream()
				.filter(e -> e.getResource().getResourceType().equals(t))
				.map(BundleEntryComponent::getResource)
				.toList();
		if (collect.size() != 1) {
			throw new PreconditionFailedException("exactly one " + t.name() + " expected");
		}
		return collect.get(0);
	}

	private boolean isSubjectExistent(final Identifier patIdentifier) {
		if (patIdentifier.getValue() == null) {
			throw new PreconditionFailedException("Composition didn't include an patient identifier");
		}

		SearchParameterMap paramMap = new SearchParameterMap();
		paramMap.add(
				"identifier",
				new TokenParam().setSystem(patIdentifier.getSystem()).setValue(patIdentifier.getValue()));

		var patients = patientDao.search(paramMap, null).getAllResources();
		if (patients.isEmpty()) {
			log.info("Subject with identifier : {}|{} not found", patIdentifier.getSystem(), patIdentifier.getValue());
			return false;
		} else {
			return true;
		}
	}

	private boolean isEncounterExistent(final Identifier encounterIdentifier) {
		if (encounterIdentifier.getValue() == null) {
			throw new PreconditionFailedException("Composition didn't include an encounter identifier");
		}
		SearchParameterMap paramMap = new SearchParameterMap();
		paramMap.add(
				"identifier",
				new TokenParam().setSystem(encounterIdentifier.getSystem()).setValue(encounterIdentifier.getValue()));

		var encounters = encounterDao.search(paramMap, null).getAllResources();
		if (encounters.isEmpty()) {
			log.warn(
					" Encounter with identifier : {}|{} not found",
					encounterIdentifier.getSystem(),
					encounterIdentifier.getValue());
			return false;
		} else {
			return true;
		}
	}

	private IParser xmlOrJson(final String str) {
		if (str.startsWith("<")) {
			return ctx.newXmlParser();
		} else if (str.startsWith("{")) {
			return ctx.newJsonParser();
		}
		throw new UnprocessableEntityException("String [" + str + "] was neither json nor XML");
	}
}
