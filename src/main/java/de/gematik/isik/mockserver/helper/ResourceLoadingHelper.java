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

import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;

@UtilityClass
public class ResourceLoadingHelper {

	public static String loadResourceAsString(String resourcePath) {
		try (var inputStream = ResourceLoadingHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (inputStream == null) {
				throw new ResourceLoadingException("Resource not found: " + resourcePath, null);
			}
			return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new ResourceLoadingException("Failed to load resource: " + resourcePath, e);
		}
	}

	public static String loadResourceAsString(String directory, String resourcePath) {
		try {
			Path fullPath = Path.of(directory, resourcePath);
			return Files.readString(fullPath);
		} catch (Exception e) {
			throw new ResourceLoadingException(
					"Failed to load resource from directory: " + directory + ", resource: " + resourcePath, e);
		}
	}

	public static class ResourceLoadingException extends RuntimeException {
		public ResourceLoadingException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
