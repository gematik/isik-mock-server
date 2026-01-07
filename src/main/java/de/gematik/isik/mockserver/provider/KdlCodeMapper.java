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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.isik.mockserver.helper.ResourceLoadingHelper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Getter
public class KdlCodeMapper {

	private ConceptMap classCodeConceptMap;
	private ConceptMap typeCodeConceptMap;
	private static final String KDL_CLASSCODE_MAP_FILENAME = "kdl-ihe-classcode.json";
	private static final String KDL_TYPECODE_MAP_FILENAME = "kdl-ihe-typecode.json";

	@PostConstruct
	public void init() {
		IParser parser = FhirContext.forR4().newJsonParser();

		String kdlClassCodeMapAsString = ResourceLoadingHelper.loadResourceAsString(KDL_CLASSCODE_MAP_FILENAME);
		classCodeConceptMap = parser.parseResource(ConceptMap.class, kdlClassCodeMapAsString);

		String kdlTypeCodeMapAsString = ResourceLoadingHelper.loadResourceAsString(KDL_TYPECODE_MAP_FILENAME);
		typeCodeConceptMap = parser.parseResource(ConceptMap.class, kdlTypeCodeMapAsString);
	}

	public Coding findTargetCoding(ConceptMap conceptMap, String sourceCode, String sourceSystem, String targetSystem) {
		for (ConceptMap.ConceptMapGroupComponent group : conceptMap.getGroup()) {
			if (group.getSource().equals(sourceSystem) && group.getTarget().equals(targetSystem)) {
				for (ConceptMap.SourceElementComponent element : group.getElement()) {
					if (element.getCode().equals(sourceCode)
							&& !element.getTarget().isEmpty()) {
						ConceptMap.TargetElementComponent target =
								element.getTarget().get(0);
						return new Coding(targetSystem, target.getCode(), target.getDisplay());
					}
				}
			}
		}
		return null;
	}
}
