package de.gematik.isik.mockserver.subscription.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Enables HAPI's built-in subscription machinery that is not auto-configured by the JPA starter.
 *
 * <p>{@code SubscriptionProcessorConfig} wires up matching and delivery of subscription
 * notifications, while {@code SubscriptionTopicConfig} adds the topic-based (R5 backport)
 * processing. Together they provide the infrastructure that this module's services build on
 * (handshake, heartbeat) and that the patient-merge operation triggers via {@code
 * SubscriptionTopicDispatcher}.
 */
@Configuration
@Import({
	ca.uhn.fhir.jpa.topic.SubscriptionTopicConfig.class,
	ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig.class
})
public class HapiSubscriptionBeans {}
