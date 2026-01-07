package de.gematik.isik.mockserver.async;

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

import de.gematik.isik.mockserver.operation.AppointmentHandlerReturnObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AsyncAppointmentBookJobService {
	private final Map<String, CompletableFuture<AppointmentHandlerReturnObject>> jobs = new ConcurrentHashMap<>();

	public void submitJob(String jobId, CompletableFuture<AppointmentHandlerReturnObject> jobFuture) {
		jobs.put(jobId, jobFuture);
	}

	public Optional<CompletableFuture<AppointmentHandlerReturnObject>> getJob(String jobId) {
		return Optional.ofNullable(jobs.get(jobId));
	}
}
