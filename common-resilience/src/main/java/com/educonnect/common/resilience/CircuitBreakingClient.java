package com.educonnect.common.resilience;

import feign.Client;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.io.IOException;
import java.net.URI;

public final class CircuitBreakingClient implements Client {

    private final Client delegate;
    private final CircuitBreakerRegistry registry;

    public CircuitBreakingClient(Client delegate, CircuitBreakerRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        CircuitBreaker breaker = registry.circuitBreaker(targetName(request));
        if (!breaker.tryAcquirePermission()) {
            throw new CircuitOpenException(breaker.getName());
        }
        long start = breaker.getCurrentTimestamp();
        try {
            Response response = delegate.execute(request, options);
            long duration = breaker.getCurrentTimestamp() - start;
            if (response.status() >= 500) {
                breaker.onError(duration, breaker.getTimestampUnit(), new DownstreamServerError(breaker.getName(), response.status()));
            } else {
                breaker.onSuccess(duration, breaker.getTimestampUnit());
            }
            return response;
        } catch (IOException | RuntimeException e) {
            breaker.onError(breaker.getCurrentTimestamp() - start, breaker.getTimestampUnit(), e);
            throw e;
        }
    }

    static String targetName(Request request) {
        String host = URI.create(request.url()).getHost();
        return host != null ? host : "unknown";
    }

    static final class DownstreamServerError extends RuntimeException {
        DownstreamServerError(String target, int status) {
            super(target + " responded " + status, null, false, false);
        }
    }
}
