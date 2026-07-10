package de.gematik.isik.mockserver.smart;

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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for EHR Launch context handles.
 *
 * <p>When an EHR initiates a SMART App Launch it creates an opaque, short-lived handle that
 * encapsulates the EHR context (patient ID, encounter ID). The app then passes this handle as the
 * {@code launch} parameter during the OAuth2 authorization request, allowing the authorization
 * server to include the matching context claims ({@code patient}, {@code encounter}) in the
 * resulting access token.
 *
 * <p>Handles expire after {@value #TTL_SECONDS} seconds and are removed lazily on retrieval or
 * periodically by the scheduled eviction task.
 *
 * <p>This implementation is suitable for a single-node mock server. A clustered deployment would
 * need a shared store (e.g. Redis).
 */
@ConditionalOnProperty(
		name = {"spring.security.oauth2.enable"},
		havingValue = "true")
@Slf4j
@Component
@EnableScheduling
public class SmartLaunchContextStore {
	/** Lifetime of a launch handle in seconds (5 minutes per SMART spec recommendation). */
	static final long TTL_SECONDS = 300L;

	/**
	 * Immutable snapshot of the EHR context bound to a launch handle.
	 *
	 * @param patientId the FHIR Patient logical ID (may be {@code null} if not provided)
	 * @param encounterId the FHIR Encounter logical ID (may be {@code null} if not provided)
	 * @param expiresAt the instant after which this context is no longer valid
	 */
	public record LaunchContext(String patientId, String encounterId, Instant expiresAt) {}

	private final Map<String, LaunchContext> store = new ConcurrentHashMap<>();
	private final Clock clock;

	/** Production constructor uses the system UTC clock. */
	public SmartLaunchContextStore() {
		this(Clock.systemUTC());
	}

	/** Constructor for testing — allows injecting a fixed or mock clock. */
	SmartLaunchContextStore(final Clock clock) {
		this.clock = clock;
	}

	/**
	 * Creates a new launch handle for the supplied EHR context and stores it for {@value
	 * #TTL_SECONDS} seconds.
	 *
	 * @param patientId the FHIR Patient logical ID (may be {@code null})
	 * @param encounterId the FHIR Encounter logical ID (may be {@code null})
	 * @return an opaque, single-use launch handle (UUID string)
	 */
	public String create(final String patientId, final String encounterId) {
		String handle = UUID.randomUUID().toString();
		Instant expiresAt = clock.instant().plusSeconds(TTL_SECONDS);
		store.put(handle, new LaunchContext(patientId, encounterId, expiresAt));
		log.debug(
				"Created launch handle {} (patient={}, encounter={}, expires={})",
				handle,
				patientId,
				encounterId,
				expiresAt);
		return handle;
	}

	/**
	 * Retrieves the {@link LaunchContext} for the given handle.
	 *
	 * <p>Returns an empty {@link Optional} if the handle is unknown or has expired. Expired entries
	 * are evicted on retrieval.
	 *
	 * @param handle the opaque launch handle returned by {@link #create}
	 * @return the associated context, or empty if absent / expired
	 */
	public Optional<LaunchContext> get(final String handle) {
		LaunchContext ctx = store.get(handle);
		if (ctx == null) {
			return Optional.empty();
		}
		if (clock.instant().isAfter(ctx.expiresAt())) {
			store.remove(handle);
			log.debug("Launch handle {} has expired and was evicted on retrieval", handle);
			return Optional.empty();
		}
		return Optional.of(ctx);
	}

	/**
	 * Consumes (removes) the launch context for the given handle.
	 *
	 * <p>Should be called after the authorization server has read the context to prevent replay.
	 *
	 * @param handle the opaque launch handle
	 * @return the consumed context, or empty if absent / expired
	 */
	public Optional<LaunchContext> consume(final String handle) {
		Optional<LaunchContext> ctx = get(handle);
		ctx.ifPresent(c -> {
			store.remove(handle);
			log.debug("Launch handle {} consumed", handle);
		});
		return ctx;
	}

	/**
	 * Scheduled eviction task: removes all entries whose {@link LaunchContext#expiresAt()} lies in
	 * the past. Runs every 60 seconds.
	 */
	@Scheduled(fixedDelay = 60_000)
	public void evictExpired() {
		Instant now = clock.instant();
		int before = store.size();
		store.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
		int removed = before - store.size();
		if (removed > 0) {
			log.debug("Evicted {} expired launch handle(s)", removed);
		}
	}

	/** Returns the number of active (non-expired) entries currently held in the store. */
	int size() {
		return store.size();
	}
}
