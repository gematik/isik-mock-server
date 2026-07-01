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

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.storage.TransactionDetails;
import de.gematik.isik.mockserver.subscription.service.SubscriptionHandshakeSender;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class SubscriptionCreateHandshakeInterceptorTest {

	private SubscriptionCreateHandshakeInterceptor interceptor;

	@Mock
	private SubscriptionHandshakeSender handshakeSender;

	@Mock
	private RequestDetails requestDetails;

	@Mock
	private TransactionDetails transactionDetails;

	private AutoCloseable closeable;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);
		interceptor = new SubscriptionCreateHandshakeInterceptor(handshakeSender);
	}

	@Test
	void testOnPreStorageCreate_NonSubscription() {
		Patient p = new Patient();
		interceptor.onPreStorageCreate(p, requestDetails, transactionDetails);

		// Non-subscription resource should be ignored completely
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
	}

	@Test
	void testOnPreStorageCreate_NonRequestedStatus() {
		Subscription sub = new Subscription();
		sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);

		interceptor.onPreStorageCreate(sub, requestDetails, transactionDetails);

		// Status should not change, program shouldn't register synchronization
		assertThat(sub.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
	}

	@Test
	void testOnPreStorageCreate_RequestedStatus_RegistersSync() {
		Subscription sub = new Subscription();
		sub.setStatus(Subscription.SubscriptionStatus.REQUESTED);

		TransactionSynchronizationManager.initSynchronization();
		try {
			interceptor.onPreStorageCreate(sub, requestDetails, transactionDetails);

			// Status should become OFF
			assertThat(sub.getStatus()).isEqualTo(Subscription.SubscriptionStatus.OFF);

			// A tag should be added
			assertThat(sub.getMeta().getTag()).hasSize(1);
			assertThat(sub.getMeta().getTagFirstRep().getSystem()).isEqualTo("urn:gematik:handshake");
			String code = sub.getMeta().getTagFirstRep().getCode();
			assertThat(code).startsWith("pending-");

			// Check synchronization registry
			List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
			assertThat(syncs).hasSize(1);

			// Trigger the sync
			syncs.get(0).afterCommit();

			// Handshake sender should be called
			verify(handshakeSender).findByMarkerAndHandshake(eq("urn:gematik:handshake"), eq(code), eq(requestDetails));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
}

