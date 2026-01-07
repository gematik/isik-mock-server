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
import ca.uhn.fhir.rest.param.CompositeParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ValueSetSearchByContextTypeValueProvider extends BaseJpaResourceProviderEncounter<ValueSet> {

	@Autowired
	IFhirResourceDao<ValueSet> valueSetDao;

	@Override
	public Class<ValueSet> getResourceType() {
		return ValueSet.class;
	}

	@PostConstruct
	public void postConstruct() {
		setDao(valueSetDao);
	}

	@Search
	public ca.uhn.fhir.rest.api.server.IBundleProvider searchByContextValueType(
			jakarta.servlet.http.HttpServletRequest theServletRequest,
			jakarta.servlet.http.HttpServletResponse theServletResponse,
			ca.uhn.fhir.rest.api.server.RequestDetails theRequestDetails,
			@Description(shortDefinition = "Context-Type-Value")
					@RequiredParam(
							name = "context-type-value",
							compositeTypes = {StringParam.class, TokenParam.class})
					CompositeParam<StringParam, TokenParam> cvt) {
		startRequest(theServletRequest);
		try {
			SearchParameterMap paramMap = new SearchParameterMap();
			paramMap.add("context", cvt.getRightValue());
			paramMap.add(
					"context-type",
					new TokenParam(
							"http://terminology.hl7.org/CodeSystem/usage-context-type",
							cvt.getLeftValue().getValue()));
			var results = valueSetDao.search(paramMap, theRequestDetails);
			if (results.isEmpty()) return new SimpleBundleProvider();

			return results;
		} finally {
			endRequest(theServletRequest);
		}
	}
}
