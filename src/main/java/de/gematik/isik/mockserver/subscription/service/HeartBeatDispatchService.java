package de.gematik.isik.mockserver.subscription.service;

import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
