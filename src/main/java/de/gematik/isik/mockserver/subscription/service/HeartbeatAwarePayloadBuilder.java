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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.searchparam.MatchUrlService;
import ca.uhn.fhir.jpa.subscription.match.registry.ActiveSubscription;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicPayloadBuilder;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicRegistry;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import de.gematik.isik.mockserver.subscription.service.HeartBeatDispatchService.NotificationType;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Overrides HAPI's {@link SubscriptionTopicPayloadBuilder} to label notifications with the correct
 * {@code SubscriptionStatus.type}.
 *
 * <p>HAPI always emits {@code type=event-notification}; this builder rewrites it based on the kind
 * signalled via {@link HeartBeatDispatchService#currentTypeOrDefault()} (e.g. {@code heartbeat} or
 * {@code handshake}). For heartbeat and handshake notifications it additionally removes the {@code
 * notification-event} and {@code events-since-subscription-start} parameters, which only apply to
 * real event notifications. Registered as {@link Primary @Primary} so it replaces the default
 * builder bean.
 */
@Primary
@Component
public class HeartbeatAwarePayloadBuilder extends SubscriptionTopicPayloadBuilder {

	public HeartbeatAwarePayloadBuilder(
			FhirContext ctx, DaoRegistry dao, SubscriptionTopicRegistry reg, MatchUrlService mus) {
		super(ctx, dao, reg, mus);
	}

	@Override
	public IBaseBundle buildPayload(
			List<IBaseResource> resources, ActiveSubscription sub, String topicUrl, RestOperationTypeEnum op) {
		IBaseBundle bundle = super.buildPayload(resources, sub, topicUrl, op);

		NotificationType type = HeartBeatDispatchService.currentTypeOrDefault();

		if (bundle instanceof org.hl7.fhir.r4.model.Bundle b && !b.getEntry().isEmpty()) {
			var first = b.getEntryFirstRep().getResource();
			if (first instanceof org.hl7.fhir.r4.model.Parameters params) {
				params.getParameter().stream()
						.filter(p ->
								"type".equals(p.getName()) && p.getValue() instanceof org.hl7.fhir.r4.model.CodeType)
						.findFirst()
						.ifPresent(p -> ((org.hl7.fhir.r4.model.CodeType) p.getValue()).setValue(mapType(type)));

				if (type == NotificationType.HEARTBEAT || type == NotificationType.HANDSHAKE) {
					params.getParameter()
							.removeIf(p -> "notification-event".equals(p.getName())
									|| "events-since-subscription-start".equals(p.getName()));
				}
			}
		}
		return bundle;
	}

	private static String mapType(NotificationType t) {
		return switch (t) {
			case HANDSHAKE -> "handshake";
			case HEARTBEAT -> "heartbeat";
			case QUERY_STATUS -> "query-status";
			case QUERY_EVENT -> "query-event";
			default -> "event-notification";
		};
	}
}
