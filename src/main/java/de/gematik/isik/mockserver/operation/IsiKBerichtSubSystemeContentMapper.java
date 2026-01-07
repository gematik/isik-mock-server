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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class IsiKBerichtSubSystemeContentMapper {

	public DocumentReference.DocumentReferenceContentComponent mapContentComponent(
			Composition composition, EncodingEnum encoding, String createdBundleId) {
		DocumentReference.DocumentReferenceContentComponent contentComponent =
				new DocumentReference.DocumentReferenceContentComponent();

		Attachment attachment = new Attachment();
		attachment.setContentType(encoding.getResourceContentTypeNonLegacy());
		attachment.setLanguage(composition.getLanguage());
		attachment.setUrl(createdBundleId);
		attachment.setCreation(composition.getDate());
		contentComponent.setAttachment(attachment);
		contentComponent.setFormat(new Coding(
				"http://ihe.net/fhir/ihe.formatcode.fhir/CodeSystem/formatcode",
				"urn:ihe:iti:xds:2017:mimeTypeSufficient",
				"mimeType Sufficient"));

		return contentComponent;
	}
}
