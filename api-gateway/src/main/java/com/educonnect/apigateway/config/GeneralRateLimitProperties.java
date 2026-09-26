package com.educonnect.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "educonnect.gateway.rate-limit")
public record GeneralRateLimitProperties(boolean enabled,
                                         int authenticatedPerMinute,
                                         int anonymousPerMinute,
                                         Duration redisTimeout) {

    public GeneralRateLimitProperties {
        authenticatedPerMinute = authenticatedPerMinute > 0 ? authenticatedPerMinute : 300;
        anonymousPerMinute = anonymousPerMinute > 0 ? anonymousPerMinute : 600;
        redisTimeout = redisTimeout != null && !redisTimeout.isZero() && !redisTimeout.isNegative() ? redisTimeout : Duration.ofMillis(300);
    }
}
