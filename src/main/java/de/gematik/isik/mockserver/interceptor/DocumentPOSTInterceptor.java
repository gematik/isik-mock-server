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
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.utils.client.ResourceFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.stream.Collectors;

@Slf4j
@Interceptor
@Component
public class DocumentPOSTInterceptor {

	@Autowired
	CompositionBundleHandler compositionHandler;

	@Autowired
	private FhirContext ctx;

	/**
	 * Override the incomingRequestPreProcessed method, which is called for each incoming request
	 * before any processing is done
	 */
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
	public boolean incomingRequestPreProcessed(
			final HttpServletRequest theRequest, final HttpServletResponse theResponse) throws IOException {
		if (StringUtils.isEmpty(theRequest.getPathInfo())
				|| theRequest.getPathInfo().equals("/")) {
			final String body = theRequest.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
			if (!body.isBlank() && compositionHandler.isDocBundle(body)) {
				checkMimeType(theRequest);
				try {
					theResponse.setContentType("application/json");
					final CompositionHandlerReturnObject returnObject = compositionHandler.handleDocBundle(body);
					theResponse.getWriter().print(returnObject.getOperationOutcome());
					if (returnObject.isOperationSuccessful()) {
						theResponse.setStatus(HttpServletResponse.SC_CREATED);
					} else {
						theResponse.setStatus(422);
					}
				} catch (final PreconditionFailedException e) {
					log.info(e.getMessage());
					theResponse.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
					final OperationOutcome outcome = new OperationOutcome();
					outcome.addIssue().setSeverity(IssueSeverity.FATAL).setDiagnostics(e.getMessage());
					ctx.newJsonParser().encodeResourceToString(outcome);
					theResponse.getWriter().print(ctx.newJsonParser().encodeResourceToString(outcome));
				}
			}
			// request handled -> return false
			return false;
		} else {
			// request not relevant, let hapi process it -> return true
			return true;
		}
	}

	private void checkMimeType(final HttpServletRequest theRequest) {
		final String contentType = theRequest.getHeader(HttpHeaders.CONTENT_TYPE);
		if (contentType == null) {
			throw new PreconditionFailedException("Content-Type Header nicht gefunden");
		}
		if (!contentType.equals(ResourceFormat.RESOURCE_XML.getHeader())
				&& !contentType.equals(ResourceFormat.RESOURCE_JSON.getHeader())) {
			throw new PreconditionFailedException("Content-Type Header nicht korrekt. War: " + contentType);
		}
	}
}
