package com.educonnect.apigateway.filter;

import com.educonnect.apigateway.config.GeneralRateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeneralRateLimitFilterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:30Z"), ZoneOffset.UTC);

    private final AtomicInteger forwarded = new AtomicInteger();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.incrementAndGet();
        return Mono.empty();
    };
    private final Map<String, Long> counters = new HashMap<>();
    private ReactiveStringRedisTemplate redis;
    private ReactiveValueOperations<String, String> ops;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        ops = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(inv -> Mono.just(counters.merge(inv.getArgument(0), 1L, Long::sum)));
        when(redis.expire(anyString(), eq(Duration.ofSeconds(70)))).thenReturn(Mono.just(true));
    }

    private GeneralRateLimitFilter filter(boolean enabled) {
        return new GeneralRateLimitFilter(new GeneralRateLimitProperties(enabled, 2, 3, Duration.ofMillis(100)), redis, CLOCK);
    }

    private static MockServerWebExchange get(String path, String ip, String userId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path).remoteAddress(new InetSocketAddress(ip, 50000));
        if (userId != null) {
            builder.header(IdentityHeaderStrippingFilter.USER_ID, userId);
        }
        return MockServerWebExchange.from(builder);
    }

    @Test
    void disabled_shouldForwardWithoutTouchingRedis() {
        filter(false).filter(get("/api/clubs", "10.0.0.1", null), chain).block();

        assertThat(forwarded).hasValue(1);
        verifyNoInteractions(redis);
    }

    @Test
    void authenticated_overLimit_shouldReturn429PerUser() {
        GeneralRateLimitFilter filter = filter(true);

        filter.filter(get("/api/clubs", "10.0.0.1", "u1"), chain).block();
        filter.filter(get("/api/clubs", "10.0.0.2", "u1"), chain).block();
        MockServerWebExchange blocked = get("/api/clubs", "10.0.0.3", "u1");
        filter.filter(blocked, chain).block();
        filter.filter(get("/api/clubs", "10.0.0.1", "u2"), chain).block();

        assertThat(forwarded).hasValue(3);
        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        String userKey = GeneralRateLimitFilter.KEY_PREFIX + "user:u1:" + (Instant.parse("2026-09-26T10:00:30Z").getEpochSecond() / 60);
        assertThat(counters).containsEntry(userKey, 3L);
        verify(redis).expire(userKey, Duration.ofSeconds(70));
    }

    @Test
    void anonymous_shouldBeLimitedPerIpWithOwnLimit() {
        GeneralRateLimitFilter filter = filter(true);

        for (int i = 0; i < 4; i++) {
            filter.filter(get("/api/auth/login", "10.0.0.9", null), chain).block();
        }
        filter.filter(get("/api/auth/login", "10.0.0.8", null), chain).block();

        assertThat(forwarded).hasValue(4);
    }

    @Test
    void nonApiPath_shouldNotBeCounted() {
        filter(true).filter(get("/actuator/health", "10.0.0.1", null), chain).block();

        assertThat(forwarded).hasValue(1);
        verifyNoInteractions(redis);
    }

    @Test
    void redisHanging_shouldFailOpenAfterShortTimeout() {
        when(ops.increment(anyString())).thenReturn(Mono.never());

        long start = System.nanoTime();
        filter(true).filter(get("/api/clubs", "10.0.0.1", "u1"), chain).block(Duration.ofSeconds(5));

        assertThat(forwarded).hasValue(1);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void redisUnavailable_shouldFailOpen() {
        when(ops.increment(anyString())).thenReturn(Mono.error(new RedisConnectionFailureException("down")));

        filter(true).filter(get("/api/clubs", "10.0.0.1", "u1"), chain).block();

        assertThat(forwarded).hasValue(1);
    }
}
