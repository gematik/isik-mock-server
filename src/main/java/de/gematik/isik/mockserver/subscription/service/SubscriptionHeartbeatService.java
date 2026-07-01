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
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Periodically sends heartbeat notifications for active subscriptions as defined by the <a
 * href="https://hl7.org/fhir/uv/subscriptions-backport/">Subscriptions R5 Backport IG</a>.
 *
 * <p>Every 60 seconds (see {@link #run()}) all {@code status=active} subscriptions are loaded and
 * filtered to those carrying a valid topic and a positive {@code backport-heartbeat-period}. Due
 * subscriptions are grouped by topic and a single heartbeat is dispatched per topic via {@link
 * HeartBeatDispatchService}. The last successful send time per subscription is tracked in {@link
 * #lastSent} to honour each subscription's individual period; entries for subscriptions that are no
 * longer active are pruned.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionHeartbeatService {

	private static final String EXT_HEARTBEAT =
			"http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-heartbeat-period";

	private final DaoRegistry daoRegistry;
	private final HeartBeatDispatchService topicNotifyService;

	private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

	@Scheduled(fixedDelayString = "PT60S")
	@Transactional
	public void run() {
		IFhirResourceDao<Subscription> subDao = daoRegistry.getResourceDao(Subscription.class);
		SystemRequestDetails srd = new SystemRequestDetails();

		SearchParameterMap map = new SearchParameterMap().add(Subscription.SP_STATUS, new TokenParam(null, "active"));

		List<IBaseResource> resources = subDao.search(map, srd).getAllResources();

		Map<String, List<Subscription>> dueByTopic = resources.stream()
				.map(r -> (Subscription) r)
				.map(sub -> new SubWithMeta(
						sub, extractBackportCanonicalOrNull(sub.getCriteria()), readHeartbeatPeriodSeconds(sub)))
				.filter(m -> m.topic() != null && m.periodSeconds() != null && m.periodSeconds() > 0)
				.filter(this::isDue)
				.collect(Collectors.groupingBy(
						SubWithMeta::topic, Collectors.mapping(SubWithMeta::sub, Collectors.toList())));

		Instant now = Instant.now();
		for (Map.Entry<String, List<Subscription>> entry : dueByTopic.entrySet()) {
			String topic = entry.getKey();
			List<Subscription> dueSubs = entry.getValue();
			if (dueSubs.isEmpty()) continue;

			int queued = topicNotifyService.dispatchHeartbeat(topic);

			if (queued > 0) {
				for (Subscription s : dueSubs) {
					String key = s.getIdElement().toUnqualifiedVersionless().getValue();
					lastSent.put(key, now);
				}
			}
		}

		cleanupStaleEntries(resources);
	}

	private boolean isDue(SubWithMeta m) {
		String key = m.sub().getIdElement().toUnqualifiedVersionless().getValue();
		Instant last = lastSent.getOrDefault(key, Instant.EPOCH);
		long elapsed = Duration.between(last, Instant.now()).getSeconds();
		return elapsed + 2 >= m.periodSeconds();
	}

	private void cleanupStaleEntries(List<IBaseResource> activeSubs) {
		Set<String> activeIds = activeSubs.stream()
				.map(r -> r.getIdElement().toUnqualifiedVersionless().getValue())
				.collect(Collectors.toSet());
		lastSent.keySet().removeIf(id -> !activeIds.contains(id));
	}

	private static String extractBackportCanonicalOrNull(String criteria) {
		if (criteria == null) return null;
		String c = criteria.trim();
		if (c.isEmpty()) return null;

		if (c.contains("?") || c.contains("&") || c.contains("=") || c.contains(" ")) {
			return null;
		}

		if (!(c.startsWith("http://") || c.startsWith("https://"))) {
			return null;
		}

		try {
			new URI(c);
		} catch (URISyntaxException e) {
			return null;
		}

		return c;
	}

	private static Integer readHeartbeatPeriodSeconds(Subscription sub) {
		if (sub.getChannel() == null) return null;
		for (Extension ext : sub.getChannel().getExtension()) {
			if (EXT_HEARTBEAT.equals(ext.getUrl()) && ext.getValue() instanceof UnsignedIntType u) {
				return u.getValue();
			}
		}
		return null;
	}

	private record SubWithMeta(Subscription sub, String topic, Integer periodSeconds) {}
}
