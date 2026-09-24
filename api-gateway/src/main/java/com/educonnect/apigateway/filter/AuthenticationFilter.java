package com.educonnect.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> implements Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFilter.class);

    private static final String UUID_SEGMENT = "[a-fA-F0-9\\-]{36}";

    private static final List<PublicEndpoint> PUBLIC_ENDPOINTS = List.of(
            new PublicEndpoint(HttpMethod.POST, "^/api/auth/(register|login|refresh|logout|forgot-password|reset-password"
                    + "|request/academician-account|request/student-account|resend-verification)$"),
            new PublicEndpoint(HttpMethod.GET, "^/api/auth/verify-email$"),
            new PublicEndpoint(HttpMethod.GET, "^/api/clubs$"),
            new PublicEndpoint(HttpMethod.GET, "^/api/clubs/" + UUID_SEGMENT + "$"),
            new PublicEndpoint(HttpMethod.GET, "^/api/events$"),
            new PublicEndpoint(HttpMethod.GET, "^/api/events/" + UUID_SEGMENT + "$"),
            new PublicEndpoint(HttpMethod.GET, "^/api/gamification/badges/[a-zA-Z_]+/image$")
    );

    private final ReactiveJwtDecoder jwtDecoder;

    public AuthenticationFilter(ReactiveJwtDecoder jwtDecoder) {
        super(Config.class);
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            if (isPublicEndpoint(request.getMethod(), path)) {
                return chain.filter(exchange);
            }

            String token = extractBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            if (token == null) {
                LOGGER.debug("Authorization header is missing/invalid for request to: {}", path);
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            return jwtDecoder.decode(token)
                    .map(Optional::of)
                    .onErrorResume(JwtException.class, e -> {
                        LOGGER.debug("Invalid token for request to: {} - Error: {}", path, e.getMessage());
                        return Mono.just(Optional.empty());
                    })
                    .flatMap(jwt -> jwt.isPresent()
                            ? forwardWithIdentity(exchange, chain, jwt.get())
                            : onError(exchange, HttpStatus.UNAUTHORIZED));
        };
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private Mono<Void> forwardWithIdentity(ServerWebExchange exchange, GatewayFilterChain chain, Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        String roles = jwt.getClaimAsString("roles");
        ServerHttpRequest newRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.set(IdentityHeaderStrippingFilter.USER_EMAIL, jwt.getSubject());
                    if (userId != null) {
                        h.set(IdentityHeaderStrippingFilter.USER_ID, userId);
                    }
                    if (roles != null) {
                        h.set(IdentityHeaderStrippingFilter.USER_ROLES, roles);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    private boolean isPublicEndpoint(HttpMethod method, String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(endpoint -> endpoint.matches(method, path));
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.length() > 1 && token.startsWith("\"") && token.endsWith("\"")) {
            token = token.substring(1, token.length() - 1).trim();
        }
        return token.isEmpty() ? null : token;
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    private record PublicEndpoint(HttpMethod method, Pattern pattern) {

        PublicEndpoint(HttpMethod method, String regex) {
            this(method, Pattern.compile(regex));
        }

        boolean matches(HttpMethod requestMethod, String path) {
            return method.equals(requestMethod) && pattern.matcher(path).matches();
        }
    }

    public static class Config {

    }
}
