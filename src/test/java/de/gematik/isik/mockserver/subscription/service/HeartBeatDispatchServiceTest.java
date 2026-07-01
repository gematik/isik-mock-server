package de.gematik.isik.mockserver.subscription.service;

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

import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import de.gematik.isik.mockserver.subscription.service.HeartBeatDispatchService.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HeartBeatDispatchServiceTest {

	private HeartBeatDispatchService service;

	@Mock
	private SubscriptionTopicDispatcher dispatcher;

	private AutoCloseable closeable;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);
		service = new HeartBeatDispatchService(dispatcher);
	}

	@Test
	void testDispatchHeartbeat() {
		when(dispatcher.dispatch(eq("test-topic"), eq(List.of()), eq(RestOperationTypeEnum.UPDATE)))
				.thenAnswer(invocation -> {
					// Verify that inside the dispatch method, the ThreadLocal type is set to HEARTBEAT
					assertThat(HeartBeatDispatchService.currentTypeOrDefault()).isEqualTo(NotificationType.HEARTBEAT);
					return 5;
				});

		int result = service.dispatchHeartbeat("test-topic");

		assertThat(result).isEqualTo(5);
		// Verify that after dispatch, the ThreadLocal is cleared (defaults back to EVENT_NOTIFICATION)
		assertThat(HeartBeatDispatchService.currentTypeOrDefault()).isEqualTo(NotificationType.EVENT_NOTIFICATION);
	}

	@Test
	void testCurrentTypeOrDefault() {
		// When TL_TYPE is not set, should default to EVENT_NOTIFICATION
		assertThat(HeartBeatDispatchService.currentTypeOrDefault()).isEqualTo(NotificationType.EVENT_NOTIFICATION);
	}
}

