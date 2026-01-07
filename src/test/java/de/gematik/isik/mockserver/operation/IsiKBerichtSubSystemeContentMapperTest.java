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

import ca.uhn.fhir.rest.api.EncodingEnum;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class IsiKBerichtSubSystemeContentMapperTest {

    @Test
	void testMapContentComponent() {
      IsiKBerichtSubSystemeContentMapper mapper = new IsiKBerichtSubSystemeContentMapper();
		Composition composition = new Composition();
		composition.setLanguage("en");
		Date creationDate = new Date();
		composition.setDate(creationDate);
		String createdBundleId = "sample-bundle-id";

		DocumentReference.DocumentReferenceContentComponent contentComponent =
			mapper.mapContentComponent(composition, EncodingEnum.JSON, createdBundleId);

		assertThat(contentComponent).isNotNull();

		Attachment attachment = contentComponent.getAttachment();
		assertThat(attachment).isNotNull();
		assertThat(attachment.getContentType()).isEqualTo(EncodingEnum.JSON.getResourceContentTypeNonLegacy());
		assertThat(attachment.getLanguage()).isEqualTo("en");
		assertThat(attachment.getUrl()).isEqualTo(createdBundleId);
		assertThat(attachment.getCreation()).isEqualTo(creationDate);

		Coding coding = contentComponent.getFormat();
		assertThat(coding).isNotNull();
		assertThat(coding.getSystem())
			.isEqualTo("http://ihe.net/fhir/ihe.formatcode.fhir/CodeSystem/formatcode");
		assertThat(coding.getCode())
			.isEqualTo("urn:ihe:iti:xds:2017:mimeTypeSufficient");
		assertThat(coding.getDisplay())
			.isEqualTo("mimeType Sufficient");
	}
}
