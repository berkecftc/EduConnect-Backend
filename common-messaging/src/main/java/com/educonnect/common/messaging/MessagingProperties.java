package com.educonnect.common.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "educonnect.messaging.retry")
public record MessagingProperties(@DefaultValue("true") boolean enabled,
                                  @DefaultValue("3") int maxAttempts,
                                  @DefaultValue("1s") Duration initialInterval,
                                  @DefaultValue("3.0") double multiplier,
                                  @DefaultValue("10s") Duration maxInterval,
                                  @DefaultValue(DeadLetters.EXCHANGE) String deadLetterExchange) {
}
