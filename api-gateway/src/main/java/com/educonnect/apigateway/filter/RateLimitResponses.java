package com.educonnect.apigateway.filter;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

final class RateLimitResponses {

    private RateLimitResponses() {
    }

    static Mono<Void> reject(ServerHttpResponse response, long retryAfterSeconds) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Çok fazla istek gönderildi. Lütfen "
                + retryAfterSeconds + " saniye sonra tekrar deneyin.\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    static String clientAddress(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }
}
