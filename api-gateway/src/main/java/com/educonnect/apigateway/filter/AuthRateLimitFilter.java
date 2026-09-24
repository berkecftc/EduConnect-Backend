package com.educonnect.apigateway.filter;

import com.educonnect.apigateway.config.AuthRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final int CLEANUP_THRESHOLD = 10_000;

    private static final Pattern LOGIN = Pattern.compile("^/api/auth/login$");
    private static final Pattern REFRESH = Pattern.compile("^/api/auth/refresh$");
    private static final Pattern SENSITIVE = Pattern.compile(
            "^/api/auth/(register|forgot-password|reset-password|request/student-account|request/academician-account)$");

    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimitFilter(AuthRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRateLimitFilter(AuthRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        if (!HttpMethod.POST.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        String path = request.getURI().getPath();
        String bucket;
        int limit;
        if (LOGIN.matcher(path).matches()) {
            bucket = "login";
            limit = properties.loginPerMinute();
        } else if (REFRESH.matcher(path).matches()) {
            bucket = "refresh";
            limit = properties.refreshPerMinute();
        } else if (SENSITIVE.matcher(path).matches()) {
            bucket = "sensitive";
            limit = properties.sensitivePerMinute();
        } else {
            return chain.filter(exchange);
        }

        long now = clock.millis();
        String key = bucket + "|" + clientAddress(request);
        Window window = windows.compute(key, (k, current) ->
                current == null || now - current.start >= WINDOW_MILLIS ? new Window(now, 1) : current.increment());
        cleanupIfNeeded(now);

        if (window.count > limit) {
            LOGGER.warn("Rate limit exceeded for {} on {}", bucket, path);
            long retryAfterSeconds = Math.max(1, (window.start + WINDOW_MILLIS - now + 999) / 1000);
            return reject(exchange.getResponse(), retryAfterSeconds);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private void cleanupIfNeeded(long now) {
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().start >= WINDOW_MILLIS);
        }
    }

    private static String clientAddress(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }

    private static Mono<Void> reject(ServerHttpResponse response, long retryAfterSeconds) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Çok fazla istek gönderildi. Lütfen "
                + retryAfterSeconds + " saniye sonra tekrar deneyin.\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private record Window(long start, int count) {

        Window increment() {
            return new Window(start, count + 1);
        }
    }
}
