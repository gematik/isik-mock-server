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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.EncodingEnum;
import de.gematik.isik.mockserver.helper.ResponseUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.IOException;
import java.util.stream.Collectors;

@UtilityClass
public class DocumentReferenceOperationCommons {

	public record ParsedRequest(IBaseResource resource, IParser parser, EncodingEnum encoding) {}

	public static ParsedRequest parseAndValidate(
			HttpServletRequest request, HttpServletResponse response, FhirContext ctx) throws IOException {
		final String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

		EncodingEnum encoding = EncodingEnum.detectEncoding(body);
		IParser parser = encoding.newParser(ctx);
		OperationOutcome outcome = new OperationOutcome();

		if (body.isBlank()) {
			outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics("Empty request body.");
			ResponseUtils.sendValidationErrorResponse(response, 400, outcome, "Empty request body.", parser, encoding);
			return null;
		}

		response.setContentType("application/json");
		IBaseResource incomingResource = parser.parseResource(body);

		return new ParsedRequest(incomingResource, parser, encoding);
	}
}
