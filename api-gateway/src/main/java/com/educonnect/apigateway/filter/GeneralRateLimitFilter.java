package com.educonnect.apigateway.filter;

import com.educonnect.apigateway.config.GeneralRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;

@Component
public class GeneralRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralRateLimitFilter.class);
    static final String KEY_PREFIX = "educonnect:gateway:rate:";
    private static final long WINDOW_SECONDS = 60;
    private static final Duration KEY_TTL = Duration.ofSeconds(WINDOW_SECONDS + 10);

    private final GeneralRateLimitProperties properties;
    private final ReactiveStringRedisTemplate redis;
    private final Clock clock;

    @Autowired
    public GeneralRateLimitFilter(GeneralRateLimitProperties properties, ReactiveStringRedisTemplate redis) {
        this(properties, redis, Clock.systemUTC());
    }

    GeneralRateLimitFilter(GeneralRateLimitProperties properties, ReactiveStringRedisTemplate redis, Clock clock) {
        this.properties = properties;
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!properties.enabled() || !request.getURI().getPath().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String userId = request.getHeaders().getFirst(IdentityHeaderStrippingFilter.USER_ID);
        String subject = userId != null && !userId.isBlank()
                ? "user:" + userId
                : "ip:" + RateLimitResponses.clientAddress(request);
        int limit = userId != null && !userId.isBlank() ? properties.authenticatedPerMinute() : properties.anonymousPerMinute();

        long nowSeconds = clock.millis() / 1000;
        long window = nowSeconds / WINDOW_SECONDS;
        String key = KEY_PREFIX + subject + ":" + window;

        return redis.opsForValue().increment(key)
                .flatMap(count -> count == 1L ? redis.expire(key, KEY_TTL).thenReturn(count) : Mono.just(count))
                .map(count -> count > limit)
                .timeout(properties.redisTimeout())
                .onErrorResume(e -> {
                    LOGGER.warn("Rate limit check skipped, Redis unavailable: {}", e.getMessage());
                    return Mono.just(false);
                })
                .flatMap(exceeded -> {
                    if (!exceeded) {
                        return chain.filter(exchange);
                    }
                    LOGGER.warn("General rate limit exceeded for {} on {}", subject.startsWith("user:") ? "user" : "ip", request.getURI().getPath());
                    long retryAfterSeconds = Math.max(1, (window + 1) * WINDOW_SECONDS - nowSeconds);
                    return RateLimitResponses.reject(exchange.getResponse(), retryAfterSeconds);
                });
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
