package com.educonnect.apigateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

final class RateLimitResponses {

    private RateLimitResponses() {
    }

    static Mono<Void> reject(ServerWebExchange exchange, long retryAfterSeconds) {
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return GatewayProblems.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Çok fazla istek gönderildi. Lütfen " + retryAfterSeconds + " saniye sonra tekrar deneyin.");
    }

    static String clientAddress(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }
}
