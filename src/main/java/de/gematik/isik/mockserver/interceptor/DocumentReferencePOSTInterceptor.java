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
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import de.gematik.isik.mockserver.helper.ReusableRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DocumentReference;
import org.springframework.stereotype.Component;

@Slf4j
@Interceptor
@Component
@RequiredArgsConstructor
public class DocumentReferencePOSTInterceptor {

	private final FhirContext ctx;
	private final DocumentReferencePOSTHelper documentReferenceHelper;

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
	public void incomingRequestPreProcessed(
			final HttpServletRequest theRequest, final RequestDetails theRequestDetails) {

		String httpMethod = theRequest.getMethod();
		if ("POST".equalsIgnoreCase(httpMethod) && theRequest.getRequestURI().matches(".*/DocumentReference/?$")) {
			String body = ((ReusableRequestWrapper) theRequest).getBody();
			EncodingEnum encoding = EncodingEnum.detectEncoding(body);
			IParser parser = encoding.newParser(ctx);

			IBaseResource resource = parser.parseResource(body);
			if (resource instanceof DocumentReference documentReference && documentReference.hasRelatesTo()) {
				documentReferenceHelper.processRelatesTo(documentReference, theRequestDetails);
			}
		}
	}
}
