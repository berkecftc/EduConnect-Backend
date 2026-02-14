package com.educonnect.apigateway.filter;

import com.educonnect.apigateway.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> implements Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFilter.class);

    // Herkese açık (public) yollar
    private final List<String> publicEndpoints = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/request/academician-account",
            "/api/auth/request/student-account"
            // Note: /api/clubs (list) and /api/clubs/{id} (detail) are handled by regex in isPublicEndpoint()
    );

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            LOGGER.info("Request path: {}", path); // log ile path kontrolü

            if (isPublicEndpoint(path)) {
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                LOGGER.warn("Authorization header is missing/invalid for request to: {}", path);
                return this.onError(exchange, "Authorization header is missing or invalid", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7).trim();
            if (token.startsWith("\"") && token.endsWith("\"") && token.length() > 1) {
                token = token.substring(1, token.length() - 1).trim();
            }

            try {
                jwtUtil.validateToken(token);
            } catch (ExpiredJwtException e) {
                LOGGER.warn("Token expired for request to: {}", path);
                return this.onError(exchange, "Token has expired", HttpStatus.UNAUTHORIZED);
            } catch (Exception e) {
                LOGGER.warn("Invalid token for request to: {} - Error: {}", path, e.getMessage());
                return this.onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
            }

            String username = jwtUtil.extractUsername(token);
            String userId = jwtUtil.extractUserId(token);
            String roles = jwtUtil.extractRoles(token);
            LOGGER.debug("Forwarding Authorization header, X-Authenticated-User-Email, X-Authenticated-User-Id and X-Authenticated-User-Roles for {}", username);

            ServerHttpRequest newRequest = request.mutate()
                    .headers(h -> {
                        h.set(HttpHeaders.AUTHORIZATION, authHeader); // explicit forward
                        h.set("X-Authenticated-User-Email", username);
                        if (userId != null) {
                            h.set("X-Authenticated-User-Id", userId);
                        }
                        if (roles != null) {
                            h.set("X-Authenticated-User-Roles", roles); // comma-separated roles
                        }
                    })
                    .build();

            return chain.filter(exchange.mutate().request(newRequest).build())
                    .contextWrite(ctx -> ctx.put("requestPath", path))
                    .doOnSuccess(aVoid -> {
                        var statusCode = exchange.getResponse().getStatusCode();
                        LOGGER.info("Response completed for path: {} with status: {}", path, statusCode);
                    })
                    .doOnError(throwable ->
                        LOGGER.error("Request failed for path: {} - Error: {}", path, throwable.getMessage())
                    );
        };
    }

    @Override
    public int getOrder() {
        return -1; // Run before Micrometer observation filters (default is 0)
    }

    private boolean isPublicEndpoint(String path) {
        // Exact match for auth endpoints
        boolean matchesPublicList = publicEndpoints.stream()
                .anyMatch(p -> path.equals(p));

        // Exact match for /api/clubs (list all clubs - public)
        boolean isClubListEndpoint = path.equals("/api/clubs");

        // Regex for /api/clubs/{uuid} (single club detail - public)
        boolean isSingleClubEndpoint = path.matches("^/api/clubs/[a-fA-F0-9\\-]{36}$");


        // 1. Tüm etkinlikleri listeleme (GET /api/events)
        boolean isEventListEndpoint = path.equals("/api/events");

        // 2. Tek etkinlik detayı (GET /api/events/{uuid})
        boolean isSingleEventEndpoint = path.matches("^/api/events/[a-fA-F0-9\\-]{36}$");

        return matchesPublicList || isClubListEndpoint || isSingleClubEndpoint ||
                isEventListEndpoint || isSingleEventEndpoint; // <-- Bunları return'e ekleyin
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    public static class Config {

    }
}
