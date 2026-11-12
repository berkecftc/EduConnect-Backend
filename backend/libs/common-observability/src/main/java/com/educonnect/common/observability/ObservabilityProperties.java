package com.educonnect.common.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "educonnect.observability")
public record ObservabilityProperties(String otlpEndpoint, Duration otlpTimeout) {

    public ObservabilityProperties {
        otlpTimeout = otlpTimeout != null ? otlpTimeout : Duration.ofSeconds(5);
    }
}
