package de.gematik.isik.mockserver.subscription.interceptor;

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

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.storage.TransactionDetails;
import de.gematik.isik.mockserver.subscription.service.SubscriptionHandshakeSender;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Subscription;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Initiates the asynchronous subscription handshake when a {@link Subscription} is created with
 * {@code status=requested}.
 *
 * <p>The handshake notification can only be sent <em>after</em> the subscription has been
 * persisted, but at that point the pre-storage hook no longer holds the stored resource. To bridge
 * this, the subscription is temporarily set to {@code status=off} and tagged with a unique marker;
 * once the surrounding transaction commits, the marker is used to look the subscription up again
 * and trigger the handshake (see {@link
 * SubscriptionHandshakeSender#findByMarkerAndHandshake(String, String)}).
 *
 * <p>The marker is internal bookkeeping with no FHIR-facing semantics and is removed again once the
 * handshake completes (see {@code SubscriptionHandshakeFinalizer#finalizeStatus}). It may linger
 * only if finalization never runs — e.g. the status was changed externally in the meantime, or the
 * marked subscription could not be found after commit.
 */
@Component
@Interceptor
@RequiredArgsConstructor
public class SubscriptionCreateHandshakeInterceptor {

	/**
	 * Tag system used as a private namespace to mark subscriptions that are awaiting their handshake.
	 * Not a standardised gematik/FHIR value — purely an internal correlation marker for this mock
	 * server. Combined with a unique {@link #MARK_CODE_PREFIX}-prefixed code, the {@code system|code}
	 * pair identifies exactly one subscription via a {@code _tag} search after the transaction
	 * commits.
	 */
	private static final String MARK_SYS = "urn:gematik:handshake";

	/**
	 * Prefix for the per-subscription marker code ({@code pending-<UUID>}), the {@code code} part of
	 * the {@link #MARK_SYS} tag.
	 */
	private static final String MARK_CODE_PREFIX = "pending-";

	private final SubscriptionHandshakeSender handshakeSender;

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void onPreStorageCreate(IBaseResource resource, RequestDetails rd, TransactionDetails tx) {
		if (!(resource instanceof Subscription sub)) {
			return;
		}
		if (sub.getStatus() != Subscription.SubscriptionStatus.REQUESTED) {
			return;
		}

		sub.setStatus(Subscription.SubscriptionStatus.OFF);

		String token = MARK_CODE_PREFIX + java.util.UUID.randomUUID();
		sub.getMeta().addTag().setSystem(MARK_SYS).setCode(token);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				handshakeSender.findByMarkerAndHandshake(MARK_SYS, token, rd);
			}
		});
	}
}
