package com.educonnect.common.resilience;

import feign.Client;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakingClientTest {

    private CircuitBreakerRegistry registry;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        FeignCircuitBreakerProperties properties = new FeignCircuitBreakerProperties(true, 50f, 4, 4, Duration.ofMinutes(1), 1);
        registry = CircuitBreakerRegistry.of(properties.toConfig());
    }

    @Test
    void execute_withClientErrors_shouldKeepCircuitClosed() throws IOException {
        Client client = new CircuitBreakingClient(respondingWith(404), registry);

        for (int i = 0; i < 10; i++) {
            assertThat(client.execute(request("http://club-service/api/clubs/x"), options()).status()).isEqualTo(404);
        }

        assertThat(registry.circuitBreaker("club-service").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(calls.get()).isEqualTo(10);
    }

    @Test
    void execute_withServerErrors_shouldOpenCircuitAndStopCallingDownstream() throws IOException {
        Client client = new CircuitBreakingClient(respondingWith(503), registry);

        for (int i = 0; i < 4; i++) {
            client.execute(request("http://user-service/api/users/internal/profiles/1"), options());
        }

        assertThat(registry.circuitBreaker("user-service").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> client.execute(request("http://user-service/api/users/internal/profiles/1"), options()))
                .isInstanceOf(CircuitOpenException.class)
                .hasMessageContaining("user-service");
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void execute_withIoFailure_shouldRecordAndRethrow() {
        Client failing = (req, opts) -> {
            calls.incrementAndGet();
            throw new ConnectException("Connection refused");
        };
        Client client = new CircuitBreakingClient(failing, registry);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.execute(request("http://course-service/api/courses"), options()))
                    .isInstanceOf(ConnectException.class);
        }

        assertThat(registry.circuitBreaker("course-service").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(registry.circuitBreaker("post-service").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void feignClient_whenCircuitOpen_shouldSurfaceAsFeignException() {
        Client failing = (req, opts) -> {
            calls.incrementAndGet();
            throw new ConnectException("Connection refused");
        };
        ProfileApi api = Feign.builder()
                .client(failing)
                .addCapability(new FeignCircuitBreakerCapability(registry))
                .retryer(Retryer.NEVER_RETRY)
                .target(ProfileApi.class, "http://user-service");

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(api::profile).isInstanceOf(FeignException.class);
        }

        assertThatThrownBy(api::profile)
                .isInstanceOf(RetryableException.class)
                .hasCauseInstanceOf(CircuitOpenException.class);
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void targetName_shouldUseServiceHost() {
        assertThat(CircuitBreakingClient.targetName(request("http://event-service/api/events/1"))).isEqualTo("event-service");
    }

    interface ProfileApi {
        @RequestLine("GET /api/users/internal/profiles/1")
        String profile();
    }

    private Client respondingWith(int status) {
        return (req, opts) -> {
            calls.incrementAndGet();
            return Response.builder()
                    .request(req)
                    .status(status)
                    .reason("test")
                    .headers(Map.of())
                    .body("{}", StandardCharsets.UTF_8)
                    .build();
        };
    }

    private static Request request(String url) {
        return Request.create(Request.HttpMethod.GET, url, Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static Request.Options options() {
        return new Request.Options();
    }
}
