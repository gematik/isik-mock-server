package de.gematik.isik.mockserver.helper;

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

import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.EncodingEnum;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.IOException;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public class ResponseUtils {

	public void sendValidationErrorResponse(
			HttpServletResponse response,
			int code,
			OperationOutcome result,
			String message,
			IParser parser,
			EncodingEnum encoding)
			throws IOException {
		String issues = result.getIssue().stream()
				.map(issue -> "["
						+ issue.getSeverity()
						+ "] - "
						+ issue.getDiagnostics()
						+ " [Location] - "
						+ issue.getLocation()
						+ " [Code] - "
						+ issue.getCode())
				.collect(Collectors.joining(", "));
		log.info("{}. Cause: {}", message, issues);

		response.reset();
		response.setStatus(code);
		response.setContentType(encoding.getResourceContentTypeNonLegacy());
		response.getWriter().print(parser.encodeResourceToString(result));
	}
}
