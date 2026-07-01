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
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SubscriptionHandshakeSenderTest {

	private SubscriptionHandshakeSender sender;

	@Mock
	private DaoRegistry daoRegistry;

	@Mock
	private SubscriptionHandshakeFinalizer finalizer;

	@Mock
	private IFhirResourceDao<Subscription> subDao;

	@Mock
	private RestTemplate restTemplate;

	private final FhirContext fhirContext = FhirContext.forR4();
	private AutoCloseable closeable;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);
		sender = new SubscriptionHandshakeSender(daoRegistry, fhirContext, finalizer);

		// Inject mock restTemplate and synchronous executor
		ReflectionTestUtils.setField(sender, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(sender, "exec", (Executor) Runnable::run);
		ReflectionTestUtils.setField(sender, "serverBaseUrl", "http://localhost:8080/fhir");

		when(daoRegistry.getResourceDao(Subscription.class)).thenReturn(subDao);
	}

	private Subscription createSubscription(String id, Subscription.SubscriptionStatus status, boolean hasRestHook, String endpoint) {
		Subscription s = new Subscription();
		s.setId(id);
		s.setStatus(status);
		s.setCriteria("https://gematik.de/fhir/isik/SubscriptionTopic/patient-merge");
		if (hasRestHook) {
			Subscription.SubscriptionChannelComponent channel = new Subscription.SubscriptionChannelComponent();
			channel.setType(Subscription.SubscriptionChannelType.RESTHOOK);
			channel.setEndpoint(endpoint);
			s.setChannel(channel);
		}
		return s;
	}

	@Test
	void testFindByMarkerAndHandshake_NoMatches() {
		IBundleProvider emptyBundle = mock(IBundleProvider.class);
		when(emptyBundle.getAllResources()).thenReturn(Collections.emptyList());
		when(subDao.search(any(), any())).thenReturn(emptyBundle);

		sender.findByMarkerAndHandshake("sys", "code", new ServletRequestDetails());

		verify(subDao, never()).read(any(IdType.class), any());
		verify(finalizer, never()).finalizeStatus(any(),any(), any(), any(), anyBoolean());
	}

	@Test
	void testFindByMarkerAndHandshake_Success() {
		Subscription sub = createSubscription("sub-1", Subscription.SubscriptionStatus.OFF, true, "http://endpoint");

		IBundleProvider searchBundle = mock(IBundleProvider.class);
		when(searchBundle.getAllResources()).thenReturn(List.of(sub));
		when(subDao.search(any(), any())).thenReturn(searchBundle);
		when(subDao.read(eq(new IdType("sub-1")), any())).thenReturn(sub);

		ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.OK);
		when(restTemplate.exchange(eq("http://endpoint"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
				.thenReturn(response);

		ServletRequestDetails requestDetails = new ServletRequestDetails();
		sender.findByMarkerAndHandshake("sys", "code", requestDetails);

		verify(finalizer).finalizeStatus(requestDetails, "sub-1", "sys", "code", true);
	}

	@Test
	void testFindByMarkerAndHandshake_RestExchangeFailure() {
		Subscription sub = createSubscription("sub-1", Subscription.SubscriptionStatus.REQUESTED, true, "http://endpoint");

		IBundleProvider searchBundle = mock(IBundleProvider.class);
		when(searchBundle.getAllResources()).thenReturn(List.of(sub));
		when(subDao.search(any(), any())).thenReturn(searchBundle);
		when(subDao.read(eq(new IdType("sub-1")), any())).thenReturn(sub);

		ResponseEntity<String> response = new ResponseEntity<>("Conflict", HttpStatus.CONFLICT);
		when(restTemplate.exchange(eq("http://endpoint"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
				.thenReturn(response);

		ServletRequestDetails requestDetails = new ServletRequestDetails();
		sender.findByMarkerAndHandshake("sys", "code", requestDetails);

		// finalizer should be called with ok = false
		verify(finalizer).finalizeStatus(requestDetails,"sub-1", "sys", "code", false);
	}

	@Test
	void testFindByMarkerAndHandshake_RestException() {
		Subscription sub = createSubscription("sub-1", Subscription.SubscriptionStatus.OFF, true, "http://endpoint");

		IBundleProvider searchBundle = mock(IBundleProvider.class);
		when(searchBundle.getAllResources()).thenReturn(List.of(sub));
		when(subDao.search(any(), any())).thenReturn(searchBundle);
		when(subDao.read(eq(new IdType("sub-1")), any())).thenReturn(sub);

		when(restTemplate.exchange(eq("http://endpoint"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
				.thenThrow(new RuntimeException("Connection timeout"));

		ServletRequestDetails requestDetails = new ServletRequestDetails();
		sender.findByMarkerAndHandshake("sys", "code",requestDetails );

		// Exception caught, finalizer called with ok = false
		verify(finalizer).finalizeStatus(requestDetails, "sub-1", "sys", "code", false);
	}

	@Test
	void testFindByMarkerAndHandshake_SkipNonPendingStatuses() {
		// active status should be skipped
		Subscription sub = createSubscription("sub-1", Subscription.SubscriptionStatus.ACTIVE, true, "http://endpoint");

		IBundleProvider searchBundle = mock(IBundleProvider.class);
		when(searchBundle.getAllResources()).thenReturn(List.of(sub));
		when(subDao.search(any(), any())).thenReturn(searchBundle);
		when(subDao.read(eq(new IdType("sub-1")), any())).thenReturn(sub);

		ServletRequestDetails requestDetails = new ServletRequestDetails();
		sender.findByMarkerAndHandshake("sys", "code", requestDetails);

		verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
		verify(finalizer, never()).finalizeStatus(any(), any(), any(), any(), anyBoolean());
	}

	@Test
	void testFindByMarkerAndHandshake_SkipMissingChannel() {
		// Missing channel/endpoint should be skipped
		Subscription sub = createSubscription("sub-1", Subscription.SubscriptionStatus.OFF, false, null);

		IBundleProvider searchBundle = mock(IBundleProvider.class);
		when(searchBundle.getAllResources()).thenReturn(List.of(sub));
		when(subDao.search(any(), any())).thenReturn(searchBundle);
		when(subDao.read(eq(new IdType("sub-1")), any())).thenReturn(sub);
		ServletRequestDetails requestDetails = new ServletRequestDetails();
		sender.findByMarkerAndHandshake("sys", "code", requestDetails);

		verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
		verify(finalizer, never()).finalizeStatus(any(), any(), any(), any(), anyBoolean());
	}
}

