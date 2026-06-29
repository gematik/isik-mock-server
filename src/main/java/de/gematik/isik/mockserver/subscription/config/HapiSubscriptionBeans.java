package de.gematik.isik.mockserver.subscription.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
	ca.uhn.fhir.jpa.topic.SubscriptionTopicConfig.class,
	ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig.class
})
public class HapiSubscriptionBeans {}
