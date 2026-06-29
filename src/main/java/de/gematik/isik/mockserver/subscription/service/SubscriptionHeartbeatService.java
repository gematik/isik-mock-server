package de.gematik.isik.mockserver.subscription.service;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.*;

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
