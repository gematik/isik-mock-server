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
import ca.uhn.fhir.rest.api.server.RequestDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

/**
 * Finalizes a subscription's status after the handshake attempt and cleans up its marker tag.
 *
 * <p>Runs in a {@link Transactional.TxType#REQUIRES_NEW new transaction} because it is triggered
 * asynchronously, after the creating transaction has already committed. It re-reads the latest
 * subscription and, only while it is still {@code off}/{@code requested}, sets the status to {@code
 * active} on a successful handshake or {@code error} otherwise, and removes the internal {@code
 * system|code} marker tag. If the status has meanwhile changed externally, finalization is skipped
 * and the marker is left in place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHandshakeFinalizer {

	private final DaoRegistry daoRegistry;

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	public void finalizeStatus(RequestDetails rd, String subscriptionId, String markSys, String markCode, boolean ok) {
		IFhirResourceDao<Subscription> subDao = daoRegistry.getResourceDao(Subscription.class);

		Subscription latest = subDao.read(new IdType(subscriptionId), rd);

		if (latest.getStatus() == Subscription.SubscriptionStatus.OFF
				|| latest.getStatus() == Subscription.SubscriptionStatus.REQUESTED) {

			latest.setStatus(ok ? Subscription.SubscriptionStatus.ACTIVE : Subscription.SubscriptionStatus.ERROR);
			latest.getMeta()
					.getTag()
					.forEach(t -> log.debug("HS tag before rm: sys='{}' code='{}'", t.getSystem(), t.getCode()));
			latest.getMeta().getTag().removeIf(t -> markSys.equals(t.getSystem()) && markCode.equals(t.getCode()));

			var outcome = subDao.update(latest, rd);
			String newVid = outcome.getId().getVersionIdPart();

			Subscription verify = subDao.read(new IdType(subscriptionId), rd);
			log.info(
					"HS finalize: id={} -> {} (version={}, tags={})",
					subscriptionId,
					verify.getStatus(),
					newVid,
					verify.getMeta().getTag().size());
		} else {
			log.debug("HS finalize skipped – status now {}", latest.getStatus());
		}
	}
}
