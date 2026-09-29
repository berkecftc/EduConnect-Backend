package com.educonnect.common.resilience;

import feign.Capability;
import feign.Client;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

public class FeignCircuitBreakerCapability implements Capability {

    private final CircuitBreakerRegistry registry;

    public FeignCircuitBreakerCapability(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Client enrich(Client client) {
        return new CircuitBreakingClient(client, registry);
    }

    public CircuitBreakerRegistry registry() {
        return registry;
    }
}
