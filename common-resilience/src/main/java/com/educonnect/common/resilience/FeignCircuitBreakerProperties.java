package com.educonnect.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "educonnect.resilience.feign.circuit-breaker")
public record FeignCircuitBreakerProperties(Boolean enabled,
                                            Float failureRateThreshold,
                                            Integer slidingWindowSize,
                                            Integer minimumNumberOfCalls,
                                            Duration waitDurationInOpenState,
                                            Integer permittedCallsInHalfOpenState) {

    public FeignCircuitBreakerProperties {
        enabled = enabled == null || enabled;
        failureRateThreshold = failureRateThreshold != null ? failureRateThreshold : 50f;
        slidingWindowSize = slidingWindowSize != null ? slidingWindowSize : 20;
        minimumNumberOfCalls = minimumNumberOfCalls != null ? minimumNumberOfCalls : 10;
        waitDurationInOpenState = waitDurationInOpenState != null ? waitDurationInOpenState : Duration.ofSeconds(20);
        permittedCallsInHalfOpenState = permittedCallsInHalfOpenState != null ? permittedCallsInHalfOpenState : 3;
    }

    public static FeignCircuitBreakerProperties defaults() {
        return new FeignCircuitBreakerProperties(null, null, null, null, null, null);
    }

    CircuitBreakerConfig toConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpenState)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .build();
    }
}
