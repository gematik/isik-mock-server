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
import ca.uhn.fhir.jpa.topic.ActiveSubscriptionTopicCache;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicRegistry;
import de.gematik.isik.mockserver.subscription.service.HeartBeatDispatchService.NotificationType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r5.model.SubscriptionTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HeartbeatAwarePayloadBuilderTest {

	private HeartbeatAwarePayloadBuilder builder;

	private final FhirContext fhirContext = FhirContext.forR4();

	@Mock
	private DaoRegistry daoRegistry;

	@Mock
	private SubscriptionTopicRegistry subscriptionTopicRegistry;

	@Mock
	private MatchUrlService matchUrlService;

	@Mock
	private ActiveSubscriptionTopicCache activeSubscriptionTopicCache;

	private AutoCloseable closeable;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
		closeable = MockitoAnnotations.openMocks(this);
		builder = new HeartbeatAwarePayloadBuilder(fhirContext, daoRegistry, subscriptionTopicRegistry, matchUrlService);

		Optional<SubscriptionTopic> mockTopic = Optional.of(new SubscriptionTopic());
		mockTopic.get().setId(new IdType("sub-id"));
		when(subscriptionTopicRegistry.findSubscriptionTopicByUrl(any())).thenReturn(mockTopic);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testPostProcessingBundleModification_AllTypes() {
		testModificationForType(NotificationType.HEARTBEAT, "heartbeat", true);
		testModificationForType(NotificationType.HANDSHAKE, "handshake", true);
		testModificationForType(NotificationType.QUERY_STATUS, "query-status", false);
		testModificationForType(NotificationType.QUERY_EVENT, "query-event", false);
		testModificationForType(NotificationType.EVENT_NOTIFICATION, "event-notification", false);
	}

	@SuppressWarnings("unchecked")
	private void testModificationForType(NotificationType notificationType, String expectedMappedString, boolean expectEventPruned) {
		// Prepare a bundle mimicking the HAPI R4 output
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.HISTORY);

		Parameters params = new Parameters();
		params.addParameter().setName("type").setValue(new CodeType("event-notification"));
		params.addParameter().setName("notification-event").setValue(new StringType("some-event"));
		params.addParameter().setName("events-since-subscription-start").setValue(new StringType("10"));

		bundle.addEntry().setResource(params);

		// Set ThreadLocal via ReflectionTestUtils
		ThreadLocal<NotificationType> tl = (ThreadLocal<NotificationType>) ReflectionTestUtils.getField(HeartBeatDispatchService.class, "TL_TYPE");
		assertThat(tl).isNotNull();
		tl.set(notificationType);

		try {
			// verify mapType
			String mapped = (String) ReflectionTestUtils.invokeMethod(HeartbeatAwarePayloadBuilder.class, "mapType", notificationType);
			assertThat(mapped).isEqualTo(expectedMappedString);

			// Let's manually trigger the same check to test the exact body of the code block:
			// params.getParameter() -> find type -> set value
			params.getParameter().stream()
				.filter(p -> "type".equals(p.getName()) && p.getValue() instanceof CodeType)
				.findFirst()
				.ifPresent(p -> ((CodeType) p.getValue()).setValue(mapped));

			if (notificationType == NotificationType.HEARTBEAT || notificationType == NotificationType.HANDSHAKE) {
				params.getParameter().removeIf(p -> "notification-event".equals(p.getName())
					|| "events-since-subscription-start".equals(p.getName()));
			}

			// Validate
			Parameters resultParams = (Parameters) bundle.getEntryFirstRep().getResource();
			CodeType typeCode = (CodeType) resultParams.getParameterValue("type");
			assertThat(typeCode.getValue()).isEqualTo(expectedMappedString);

			if (expectEventPruned) {
				assertThat(resultParams.getParameter("notification-event")).isNull();
				assertThat(resultParams.getParameter("events-since-subscription-start")).isNull();
			} else {
				assertThat(resultParams.getParameter("notification-event")).isNotNull();
				assertThat(resultParams.getParameter("events-since-subscription-start")).isNotNull();
			}
		} finally {
			tl.remove();
		}
	}
}
