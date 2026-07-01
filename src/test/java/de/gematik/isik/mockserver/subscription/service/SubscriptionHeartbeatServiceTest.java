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

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubscriptionHeartbeatServiceTest {

	private SubscriptionHeartbeatService service;

	@Mock
	private DaoRegistry daoRegistry;

	@Mock
	private HeartBeatDispatchService topicNotifyService;

	@Mock
	private IFhirResourceDao<Subscription> subDao;

	private AutoCloseable closeable;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);
		service = new SubscriptionHeartbeatService(daoRegistry, topicNotifyService);
		when(daoRegistry.getResourceDao(Subscription.class)).thenReturn(subDao);
	}

	private Subscription createSubscription(String id, String criteria, Integer heartbeatPeriodSeconds) {
		Subscription s = new Subscription();
		s.setId(id);
		s.setStatus(Subscription.SubscriptionStatus.ACTIVE);
		s.setCriteria(criteria);

		if (heartbeatPeriodSeconds != null) {
			Subscription.SubscriptionChannelComponent channel = new Subscription.SubscriptionChannelComponent();
			channel.setType(Subscription.SubscriptionChannelType.RESTHOOK);
			channel.setEndpoint("http://localhost");

			Extension ext = new Extension();
			ext.setUrl("http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-heartbeat-period");
			ext.setValue(new UnsignedIntType(heartbeatPeriodSeconds));
			channel.addExtension(ext);

			s.setChannel(channel);
		}
		return s;
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRun_SendsHeartbeatAndPrunesStale() {
		// Prepare subscription 1 (due, because not in lastSent map)
		Subscription sub1 = createSubscription("sub-1", "https://gematik.de/fhir/isik/SubscriptionTopic/patient-merge", 60);
		// Prepare subscription 2 (invalid criteria style with params, should be skipped)
		Subscription sub2 = createSubscription("sub-2", "https://invalid.com?param=true", 30);
		// Prepare subscription 3 (missing heartbeat period extension, should be skipped)
		Subscription sub3 = createSubscription("sub-3", "https://gematik.de/fhir/isik/SubscriptionTopic/patient-merge", null);

		IBundleProvider bundleProvider = mock(IBundleProvider.class);
		List<IBaseResource> activeSubs = List.of(sub1, sub2, sub3);
		when(bundleProvider.getAllResources()).thenReturn(activeSubs);
		when(subDao.search(any(), any())).thenReturn(bundleProvider);

		// Mock the notify service to return 1 queued heartbeat
		when(topicNotifyService.dispatchHeartbeat("https://gematik.de/fhir/isik/SubscriptionTopic/patient-merge")).thenReturn(1);

		// Populate other stale entries in lastSent map to prove they get pruned
		Map<String, Instant> lastSentMap = (Map<String, Instant>) ReflectionTestUtils.getField(service, "lastSent");
		assertThat(lastSentMap).isNotNull();
		lastSentMap.put("stale-sub", Instant.now());
		lastSentMap.put("sub-1", Instant.now().minusSeconds(120)); // extremely due

		service.run();

		// Check dispatch was called for the valid topic
		verify(topicNotifyService).dispatchHeartbeat("https://gematik.de/fhir/isik/SubscriptionTopic/patient-merge");
		// Check invalid topics are not dispatched
		verify(topicNotifyService, never()).dispatchHeartbeat("https://invalid.com?param=true");

		// "sub-1" should be tracked in lastSent map now,
		// and stale entries like "stale-sub" should have been cleaned up because they are not in the active resources list
		assertThat(lastSentMap).containsKey("sub-1");
		assertThat(lastSentMap).doesNotContainKey("stale-sub");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRun_NotDue() {
		Subscription sub1 = createSubscription("sub-1", "https://gematik.de/fhir/isik/SubscriptionTopic/patient-merge", 60);

		IBundleProvider bundleProvider = mock(IBundleProvider.class);
		when(bundleProvider.getAllResources()).thenReturn(List.of(sub1));
		when(subDao.search(any(), any())).thenReturn(bundleProvider);

		// Explicitly mark as recently sent (not due)
		Map<String, Instant> lastSentMap = (Map<String, Instant>) ReflectionTestUtils.getField(service, "lastSent");
		assertThat(lastSentMap).isNotNull();
		lastSentMap.put("sub-1", Instant.now()); // sent just now

		service.run();

		// Should not dispatch because sub-1 is not due
		verify(topicNotifyService, never()).dispatchHeartbeat(anyString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testExtractBackportCanonicalOrNull() {
		// Valid canonicals
		assertThat(extractUrl("http://example.com/topic")).isEqualTo("http://example.com/topic");
		assertThat(extractUrl("https://example.com")).isEqualTo("https://example.com");

		// Invalid canonicals
		assertThat(extractUrl(null)).isNull();
		assertThat(extractUrl("")).isNull();
		assertThat(extractUrl("   ")).isNull();
		assertThat(extractUrl("test?param=1")).isNull();
		assertThat(extractUrl("not-an-url")).isNull();
		assertThat(extractUrl("http://example.com/topic&other=1")).isNull();
		assertThat(extractUrl("http://test=1")).isNull();
		assertThat(extractUrl("http://spaces inside")).isNull();
		assertThat(extractUrl("invalid_uri:\\\\")).isNull();
	}

	private String extractUrl(String criteria) {
		// Use Reflection to invoke private static method extractBackportCanonicalOrNull
		return (String) ReflectionTestUtils.invokeMethod(SubscriptionHeartbeatService.class, "extractBackportCanonicalOrNull", criteria);
	}
}

