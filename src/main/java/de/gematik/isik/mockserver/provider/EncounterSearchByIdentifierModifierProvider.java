package de.gematik.isik.mockserver.provider;

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

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProviderEncounter;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.Account;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EncounterSearchByIdentifierModifierProvider extends BaseJpaResourceProviderEncounter<Encounter> {

	@Autowired
	IFhirResourceDao<Encounter> encounterDao;

	@Autowired
	IFhirResourceDao<Account> accountDao;

	@Autowired
	IFhirResourceDao<Patient> patientDao;

	@Override
	public Class<Encounter> getResourceType() {
		return Encounter.class;
	}

	@PostConstruct
	public void postConstruct() {
		setDao(encounterDao);
	}

	@Search
	public ca.uhn.fhir.rest.api.server.IBundleProvider searchByAccountIdentifier(
			jakarta.servlet.http.HttpServletRequest theServletRequest,
			jakarta.servlet.http.HttpServletResponse theServletResponse,
			ca.uhn.fhir.rest.api.server.RequestDetails theRequestDetails,
			@Description(shortDefinition = "Account identifier") @RequiredParam(name = "account:identifier")
					TokenParam accountIdentifier) {
		startRequest(theServletRequest);
		try {
			SearchParameterMap paramMap = new SearchParameterMap();
			paramMap.add("identifier", accountIdentifier);
			var accounts = accountDao.search(paramMap, theRequestDetails);
			if (accounts.isEmpty()) return new SimpleBundleProvider();

			paramMap = new SearchParameterMap();
			var account = (Account) accounts.getAllResources().get(0);
			paramMap.add("account", new ReferenceParam(account.getId()));
			return encounterDao.search(paramMap, theRequestDetails, theServletResponse);
		} finally {
			endRequest(theServletRequest);
		}
	}

	@Search
	public ca.uhn.fhir.rest.api.server.IBundleProvider searchByPatientIdentifier(
			jakarta.servlet.http.HttpServletRequest theServletRequest,
			jakarta.servlet.http.HttpServletResponse theServletResponse,
			ca.uhn.fhir.rest.api.server.RequestDetails theRequestDetails,
			@Description(shortDefinition = "Patient identifier") @RequiredParam(name = "patient:identifier")
					TokenParam patientIdentifier) {
		startRequest(theServletRequest);
		try {
			SearchParameterMap paramMap = new SearchParameterMap();
			paramMap.add("identifier", patientIdentifier);
			var patients = patientDao.search(paramMap, theRequestDetails);
			if (patients.isEmpty()) return new SimpleBundleProvider();

			paramMap = new SearchParameterMap();
			var patient = (Patient) patients.getAllResources().get(0);
			paramMap.add("subject", new ReferenceParam("Patient/" + patient.getIdPart()));
			return encounterDao.search(paramMap, theRequestDetails, theServletResponse);
		} finally {
			endRequest(theServletRequest);
		}
	}
}
