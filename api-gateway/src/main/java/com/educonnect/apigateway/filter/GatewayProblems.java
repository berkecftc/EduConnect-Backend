package com.educonnect.apigateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class GatewayProblems {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GatewayProblems() {
    }

    static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body(status, errorCode, message,
                exchange.getRequest().getURI().getPath()));
        return response.writeWith(Mono.just(buffer));
    }

    static byte[] body(HttpStatus status, String errorCode, String message, String path) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", message);
        problem.put("instance", path);
        problem.put("errorCode", errorCode);
        problem.put("message", message);
        problem.put("timestamp", Instant.now().toString());
        try {
            return OBJECT_MAPPER.writeValueAsBytes(problem);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Problem body could not be serialized", ex);
        }
    }
}
