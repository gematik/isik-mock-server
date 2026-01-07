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

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KdlCodeMapperTest {

	private final KdlCodeMapper mapper = new KdlCodeMapper();

	@Test
	void testFindTargetCodingFound() {
		ConceptMap conceptMap = new ConceptMap();
		ConceptMap.ConceptMapGroupComponent group = conceptMap.addGroup();
		group.setSource("sourceSystem");
		group.setTarget("targetSystem");

		ConceptMap.SourceElementComponent element = group.addElement();
		element.setCode("sourceCode");
		ConceptMap.TargetElementComponent targetElement = element.addTarget();
		targetElement.setCode("targetCode");
		targetElement.setDisplay("targetDisplay");

		Coding coding = mapper.findTargetCoding(conceptMap, "sourceCode", "sourceSystem", "targetSystem");

		assertThat(coding).isNotNull();
		assertThat(coding.getSystem()).isEqualTo("targetSystem");
		assertThat(coding.getCode()).isEqualTo("targetCode");
		assertThat(coding.getDisplay()).isEqualTo("targetDisplay");
	}

	@Test
	void testFindTargetCodingGroupNotFound() {
		ConceptMap conceptMap = new ConceptMap();
		ConceptMap.ConceptMapGroupComponent group = conceptMap.addGroup();
		group.setSource("differentSource");
		group.setTarget("differentTarget");

		ConceptMap.SourceElementComponent element = group.addElement();
		element.setCode("sourceCode");
		ConceptMap.TargetElementComponent targetElement = element.addTarget();
		targetElement.setCode("targetCode");
		targetElement.setDisplay("targetDisplay");

		Coding coding = mapper.findTargetCoding(conceptMap, "sourceCode", "sourceSystem", "targetSystem");
		assertThat(coding).isNull();
	}

	@Test
	void testFindTargetCodingElementNotFound() {
		ConceptMap conceptMap = new ConceptMap();
		ConceptMap.ConceptMapGroupComponent group = conceptMap.addGroup();
		group.setSource("sourceSystem");
		group.setTarget("targetSystem");

		ConceptMap.SourceElementComponent element = group.addElement();
		element.setCode("differentCode");
		ConceptMap.TargetElementComponent targetElement = element.addTarget();
		targetElement.setCode("targetCode");
		targetElement.setDisplay("targetDisplay");

		Coding coding = mapper.findTargetCoding(conceptMap, "sourceCode", "sourceSystem", "targetSystem");
		assertThat(coding).isNull();
	}

	@Test
	void testFindTargetCodingNoTarget() {
		ConceptMap conceptMap = new ConceptMap();
		ConceptMap.ConceptMapGroupComponent group = conceptMap.addGroup();
		group.setSource("sourceSystem");
		group.setTarget("targetSystem");

		group.addElement().setCode("sourceCode");

		Coding coding = mapper.findTargetCoding(conceptMap, "sourceCode", "sourceSystem", "targetSystem");
		assertThat(coding).isNull();
	}

	@Test
	void testInit() {
		mapper.init();
		assertThat(mapper.getClassCodeConceptMap()).isNotNull();
		assertThat(mapper.getTypeCodeConceptMap()).isNotNull();
	}

	@Test
	void testFindTargetCodingXdsClassValid() {
		mapper.init();
		ConceptMap conceptMap = mapper.getClassCodeConceptMap();
		Coding coding = mapper.findTargetCoding(conceptMap, "PT130102", "http://dvmd.de/fhir/CodeSystem/kdl", "http://ihe-d.de/CodeSystems/IHEXDSclassCode");

		assertThat(coding).isNotNull();
		assertThat(coding.getSystem()).isEqualTo("http://ihe-d.de/CodeSystems/IHEXDSclassCode");
		assertThat(coding.getCode()).isEqualTo("BEF");
		assertThat(coding.getDisplay()).isEqualTo("Befundbericht");
	}

	@Test
	void testFindTargetCodingXdsTypeValid() {
		mapper.init();
		ConceptMap conceptMap = mapper.getTypeCodeConceptMap();
		Coding coding = mapper.findTargetCoding(conceptMap, "PT130102", "http://dvmd.de/fhir/CodeSystem/kdl", "http://ihe-d.de/CodeSystems/IHEXDStypeCode");

		assertThat(coding).isNotNull();
		assertThat(coding.getSystem()).isEqualTo("http://ihe-d.de/CodeSystems/IHEXDStypeCode");
		assertThat(coding.getCode()).isEqualTo("PATH");
		assertThat(coding.getDisplay()).isEqualTo("Pathologiebefundberichte");
	}
}
