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

import ca.uhn.fhir.validation.SingleValidationMessage;
import de.gematik.refv.commons.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class FhirValidationUtilsTest {

	@Test
	void getValidationResult_shouldReturnValidResult_whenAnyValidationResultIsValid() {
		SingleValidationMessage mockMessage = Mockito.mock(SingleValidationMessage.class);
		List<SingleValidationMessage> allValidationMessages = List.of(mockMessage);

		ValidationResult validResult = Mockito.mock(ValidationResult.class);
		CompletableFuture<ValidationResult> validFuture = CompletableFuture.completedFuture(validResult);

		Mockito.when(validResult.isValid()).thenReturn(true);

		List<CompletableFuture<ValidationResult>> futures = List.of(validFuture);

		ValidationResult result = FhirValidationUtils.getValidationResult(allValidationMessages, futures);

		assertThat(result).isEqualTo(validResult);
	}

	@Test
	void getValidationResult_shouldReturnNewValidationResult_whenNoResultIsValid() {
		SingleValidationMessage mockMessage = Mockito.mock(SingleValidationMessage.class);
		List<SingleValidationMessage> allValidationMessages = List.of(mockMessage);

		ValidationResult invalidResult = Mockito.mock(ValidationResult.class);
		CompletableFuture<ValidationResult> invalidFuture = CompletableFuture.completedFuture(invalidResult);

		Mockito.when(invalidResult.isValid()).thenReturn(false);

		List<CompletableFuture<ValidationResult>> futures = List.of(invalidFuture);

		ValidationResult result = FhirValidationUtils.getValidationResult(allValidationMessages, futures);

		assertThat(result.getValidationMessages()).isEqualTo(allValidationMessages);
	}

	@Test
	void getValidationResult_shouldReturnNewValidationResult_whenNoFuturesProvided() {
		SingleValidationMessage mockMessage = Mockito.mock(SingleValidationMessage.class);
		List<SingleValidationMessage> allValidationMessages = List.of(mockMessage);

		ValidationResult result = FhirValidationUtils.getValidationResult(allValidationMessages, List.of());

		assertThat(result.getValidationMessages()).isEqualTo(allValidationMessages);
	}
}
