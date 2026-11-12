package com.educonnect.common.messaging.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "educonnect.messaging.outbox")
public record OutboxProperties(@DefaultValue("true") boolean enabled,
                               @DefaultValue("2s") Duration pollInterval,
                               @DefaultValue("50") int batchSize,
                               @DefaultValue("5s") Duration confirmTimeout,
                               @DefaultValue("7d") Duration retention) {
}
