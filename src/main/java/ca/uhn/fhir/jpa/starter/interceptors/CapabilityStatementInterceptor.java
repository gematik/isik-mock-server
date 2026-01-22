package ca.uhn.fhir.jpa.starter.interceptors;

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

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations;

/**
 * Interceptor to customize the FHIR Capability Statement.
 */
@Interceptor
public class CapabilityStatementInterceptor {

	private static final String PUBLISHER = "gematik GmbH";
	private static final String DESCRIPTION = "This is a FHIR Mock Server conform to the gematik ISiK specifications.";
	private static final String COPYRIGHT_TEXT = "Copyright © 2026 - " + PUBLISHER;
	private static final String SEARCH_PARAM_COUNT_NAME = "_count";
	private static final String SEARCH_PARAM_COUNT_DOCUMENTATION = "Specifies the number of resources to return, per page";

	/**
	 * Customize the Capability Statement by adding additional information and extending search parameters.
	 * @param theCapabilityStatement an instance of {@link IBaseConformance} representing the Capability Statement
	 */
	@Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
	public void customize(IBaseConformance theCapabilityStatement) {

		CapabilityStatement cs = (CapabilityStatement) theCapabilityStatement;

		cs.getSoftware().setName("ISIK FHIR Mock Server");

		cs.setPublisher(PUBLISHER);
		cs.setDescription(DESCRIPTION);
		cs.setCopyright(COPYRIGHT_TEXT);

		final var restElements = cs.getRest();
		for (var component : restElements) {
			final var resource = component.getResource();
			for (var res : resource) {
				res.addSearchParam()
						.setName(SEARCH_PARAM_COUNT_NAME)
						.setType(Enumerations.SearchParamType.NUMBER)
						.setDocumentation(SEARCH_PARAM_COUNT_DOCUMENTATION);
			}
		}
	}
}
