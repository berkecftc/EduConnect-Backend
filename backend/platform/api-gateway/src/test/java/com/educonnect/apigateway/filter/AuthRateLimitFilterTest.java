package com.educonnect.apigateway.filter;

import com.educonnect.apigateway.config.AuthRateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private final AtomicInteger forwarded = new AtomicInteger();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.incrementAndGet();
        return Mono.empty();
    };

    private static MockServerWebExchange post(String path, String ip) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress(ip, 50000)));
    }

    private static AuthRateLimitFilter filter(boolean enabled, Clock clock) {
        return new AuthRateLimitFilter(new AuthRateLimitProperties(enabled, 3, 2, 5), clock);
    }

    @Test
    void disabled_shouldForwardEverything() {
        AuthRateLimitFilter filter = filter(false, Clock.systemUTC());

        for (int i = 0; i < 10; i++) {
            filter.filter(post("/api/auth/login", "10.0.0.1"), chain).block();
        }

        assertThat(forwarded).hasValue(10);
    }

    @Test
    void login_overLimit_shouldReturn429WithRetryAfter() {
        AuthRateLimitFilter filter = filter(true, Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));

        for (int i = 0; i < 3; i++) {
            filter.filter(post("/api/auth/login", "10.0.0.1"), chain).block();
        }
        MockServerWebExchange blocked = post("/api/auth/login", "10.0.0.1");
        filter.filter(blocked, chain).block();

        assertThat(forwarded).hasValue(3);
        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    @Test
    void limits_shouldBeTrackedPerClientAddress() {
        AuthRateLimitFilter filter = filter(true, Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));

        for (int i = 0; i < 3; i++) {
            filter.filter(post("/api/auth/login", "10.0.0.1"), chain).block();
        }
        filter.filter(post("/api/auth/login", "10.0.0.2"), chain).block();

        assertThat(forwarded).hasValue(4);
    }

    @Test
    void window_shouldResetAfterOneMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
        AuthRateLimitFilter filter = filter(true, clock);

        for (int i = 0; i < 4; i++) {
            filter.filter(post("/api/auth/forgot-password", "10.0.0.1"), chain).block();
        }
        clock.advanceMillis(AuthRateLimitFilter.WINDOW_MILLIS);
        filter.filter(post("/api/auth/forgot-password", "10.0.0.1"), chain).block();

        assertThat(forwarded).hasValue(3);
    }

    @Test
    void otherPaths_shouldNotBeLimited() {
        AuthRateLimitFilter filter = filter(true, Clock.systemUTC());

        for (int i = 0; i < 10; i++) {
            filter.filter(post("/api/posts", "10.0.0.1"), chain).block();
        }

        assertThat(forwarded).hasValue(10);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
