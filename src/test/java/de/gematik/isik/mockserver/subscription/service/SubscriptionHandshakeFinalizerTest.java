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
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SubscriptionHandshakeFinalizerTest {

	private SubscriptionHandshakeFinalizer finalizer;

	@Mock
	private DaoRegistry daoRegistry;

	@Mock
	private IFhirResourceDao<Subscription> subDao;

	private AutoCloseable closeable;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);
		when(daoRegistry.getResourceDao(Subscription.class)).thenReturn(subDao);
		finalizer = new SubscriptionHandshakeFinalizer(daoRegistry);
	}

	@Test
	void testFinalize_OffStatus_Success() {
		Subscription sub = new Subscription();
		sub.setStatus(Subscription.SubscriptionStatus.OFF);

		Coding tag = new Coding("mark-sys", "mark-code", "display");
		sub.getMeta().addTag(tag);

		when(subDao.read(eq(new IdType("sub-1")), any())).thenReturn(sub);

		DaoMethodOutcome outcome = mock(DaoMethodOutcome.class);
		IIdType returnedId = mock(IIdType.class);
		ServletRequestDetails requestDetails = new ServletRequestDetails();

		when(returnedId.getVersionIdPart()).thenReturn("2");
		when(outcome.getId()).thenReturn(returnedId);
		when(subDao.update(any(Subscription.class), any(RequestDetails.class))).thenReturn(outcome);

		finalizer.finalizeStatus(requestDetails, "sub-1", "mark-sys", "mark-code", true);

		assertThat(sub.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
		assertThat(sub.getMeta().getTag()).isEmpty();

		verify(subDao).update(sub, requestDetails);
	}

	@Test
	void testFinalize_RequestedStatus_Unsuccessful() {
		Subscription sub = new Subscription();
		sub.setStatus(Subscription.SubscriptionStatus.REQUESTED);

		Coding tag1 = new Coding("mark-sys", "mark-code", "display");
		Coding tag2 = new Coding("other-sys", "other-code", "display");
		sub.getMeta().addTag(tag1);
		sub.getMeta().addTag(tag2);

		ServletRequestDetails requestDetails = new ServletRequestDetails();

		when(subDao.read(eq(new IdType("sub-1")), any(RequestDetails.class))).thenReturn(sub);

		DaoMethodOutcome outcome = mock(DaoMethodOutcome.class);
		IIdType returnedId = mock(IIdType.class);
		when(returnedId.getVersionIdPart()).thenReturn("3");
		when(outcome.getId()).thenReturn(returnedId);
		when(subDao.update(any(Subscription.class), any(RequestDetails.class))).thenReturn(outcome);

		finalizer.finalizeStatus(requestDetails, "sub-1", "mark-sys", "mark-code", false);

		assertThat(sub.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ERROR);
		// mark-sys tag is removed, other-sys tag remains
		assertThat(sub.getMeta().getTag()).hasSize(1);
		assertThat(sub.getMeta().getTagFirstRep().getSystem()).isEqualTo("other-sys");

		verify(subDao).update(sub, requestDetails);
	}

	@Test
	void testFinalize_ActiveStatus_Skip() {
		Subscription sub = new Subscription();
		sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);

		Coding tag = new Coding("mark-sys", "mark-code", "display");
		sub.getMeta().addTag(tag);

		ServletRequestDetails requestDetails = new ServletRequestDetails();
		when(subDao.read(eq(new IdType("sub-1")), any(RequestDetails.class))).thenReturn(sub);

		finalizer.finalizeStatus(requestDetails, "sub-1", "mark-sys", "mark-code", true);

		// Skipped finalization, status and tag stay the same
		assertThat(sub.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
		assertThat(sub.getMeta().getTag()).hasSize(1);

		verify(subDao, never()).update(any(), eq(requestDetails));
	}
}

