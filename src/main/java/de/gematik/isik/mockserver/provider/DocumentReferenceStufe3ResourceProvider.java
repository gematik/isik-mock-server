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
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import de.gematik.isik.mockserver.helper.OperationOutcomeUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static de.gematik.isik.mockserver.provider.DocumentReferenceStufe3ResourceProviderHelper.XDS_TYPE_CODE_SYSTEM;

@Slf4j
@Component
public class DocumentReferenceStufe3ResourceProvider
		extends ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider<DocumentReference> {

	@Autowired
	private IFhirResourceDao<DocumentReference> documentReferenceDao;

	@Autowired
	private DocumentReferenceStufe3ResourceProviderHelper helper;

	@PostConstruct
	public void postConstruct() {
		setDao(documentReferenceDao);
	}

	@Create
	@Override
	public MethodOutcome create(
			HttpServletRequest theRequest,
			@ResourceParam DocumentReference theResource,
			@ConditionalUrlParam String theConditional,
			RequestDetails theRequestDetails) {
		startRequest(theRequest);
		try {
			if (theResource.getContent().isEmpty()) {
				throw new IllegalArgumentException("No content element found");
			}

			OperationOutcome outcome = new OperationOutcome();

			helper.validatePatient(theResource, outcome, theRequestDetails);
			helper.validateEncounter(theResource, outcome, theRequestDetails);

			Attachment attachment = theResource.getContent().getFirst().getAttachment();
			byte[] base64data = attachment.getData();
			helper.validateBase64Data(base64data, outcome);
			attachment.setUrl(helper.createBinaryResourceAndGetUrl(base64data, theRequestDetails));
			attachment.setData(null);

			var kdlTypeCode = helper.getKDLTypeCode(theResource, outcome);

			if (theResource.getType().getCoding().stream()
					.noneMatch(c -> c.getSystem().equals(XDS_TYPE_CODE_SYSTEM))) {
				helper.mapKdlCodeToXdsType(kdlTypeCode, theResource.getType().getCoding(), outcome);
			}

			if (theResource.getCategory().isEmpty()) {
				helper.mapKdlCodeToXdsClass(kdlTypeCode, theResource.getCategory(), outcome);
			}

			if (OperationOutcomeUtils.hasErrorIssue(outcome)) {
				throw new UnprocessableEntityException("Invalid request body", outcome);
			}

			if (theConditional != null) {
				return getDao().create(theResource, theConditional, theRequestDetails);
			} else {
				return getDao().create(theResource, theRequestDetails);
			}
		} finally {
			endRequest(theRequest);
		}
	}
}
