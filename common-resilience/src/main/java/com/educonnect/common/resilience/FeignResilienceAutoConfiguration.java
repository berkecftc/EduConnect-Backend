package com.educonnect.common.resilience;

import feign.Capability;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({Capability.class, CircuitBreakerRegistry.class})
@ConditionalOnProperty(prefix = "educonnect.resilience.feign.circuit-breaker", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FeignCircuitBreakerProperties.class)
public class FeignResilienceAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeignResilienceAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public FeignCircuitBreakerCapability feignCircuitBreakerCapability(FeignCircuitBreakerProperties properties) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(properties.toConfig());
        registry.getEventPublisher().onEntryAdded(added -> added.getAddedEntry().getEventPublisher()
                .onStateTransition(event -> LOGGER.warn("Circuit breaker {}: {}",
                        event.getCircuitBreakerName(), event.getStateTransition())));
        return new FeignCircuitBreakerCapability(registry);
    }
}
