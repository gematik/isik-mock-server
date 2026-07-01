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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Dispatches heartbeat notifications through HAPI's {@link SubscriptionTopicDispatcher}.
 *
 * <p>HAPI builds the same {@code SubscriptionStatus} payload regardless of the notification kind,
 * so the intended kind is signalled out-of-band: before dispatching, the type is stored in a {@link
 * ThreadLocal}, which {@link HeartbeatAwarePayloadBuilder} reads on the same thread to set the
 * status {@code type} (and to strip event-only fields for heartbeats). The dispatch sends an empty
 * resource list, producing a notification with no events. The {@link ThreadLocal} is always cleared
 * afterwards; {@link #currentTypeOrDefault()} falls back to {@code EVENT_NOTIFICATION} when unset.
 */
@Service
@RequiredArgsConstructor
public class HeartBeatDispatchService {

	public enum NotificationType {
		HANDSHAKE,
		HEARTBEAT,
		EVENT_NOTIFICATION,
		QUERY_STATUS,
		QUERY_EVENT
	}

	private static final ThreadLocal<NotificationType> TL_TYPE = new ThreadLocal<>();

	private final SubscriptionTopicDispatcher dispatcher;

	public int dispatchHeartbeat(String topicUrl) {
		TL_TYPE.set(NotificationType.HEARTBEAT);
		try {
			return dispatcher.dispatch(topicUrl, List.of(), RestOperationTypeEnum.UPDATE);
		} finally {
			TL_TYPE.remove();
		}
	}

	static NotificationType currentTypeOrDefault() {
		return Optional.ofNullable(TL_TYPE.get()).orElse(NotificationType.EVENT_NOTIFICATION);
	}
}
