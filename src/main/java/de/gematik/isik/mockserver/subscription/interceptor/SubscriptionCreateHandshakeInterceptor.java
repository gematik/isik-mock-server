package de.gematik.isik.mockserver.subscription.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.storage.TransactionDetails;
import de.gematik.isik.mockserver.subscription.service.SubscriptionHandshakeSender;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Subscription;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Interceptor
@RequiredArgsConstructor
public class SubscriptionCreateHandshakeInterceptor {

	private static final String MARK_SYS = "urn:gematik:handshake";
	private static final String MARK_CODE_PREFIX = "pending-";

	private final SubscriptionHandshakeSender handshakeSender;

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void onPreStorageCreate(IBaseResource resource, RequestDetails rd, TransactionDetails tx) {
		if (!(resource instanceof Subscription sub)) {
			return;
		}
		if (sub.getStatus() != Subscription.SubscriptionStatus.REQUESTED) {
			return;
		}

		sub.setStatus(Subscription.SubscriptionStatus.OFF);

		String token = MARK_CODE_PREFIX + java.util.UUID.randomUUID();
		sub.getMeta().addTag().setSystem(MARK_SYS).setCode(token);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				handshakeSender.findByMarkerAndHandshake(MARK_SYS, token);
			}
		});
	}
}
