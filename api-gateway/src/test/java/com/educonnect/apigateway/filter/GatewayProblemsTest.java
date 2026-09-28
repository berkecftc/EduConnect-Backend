package com.educonnect.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayProblemsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void internalPath_shouldReturnNotFoundProblem() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/internal/profiles/1").build());
        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain must not be called"));

        new InternalPathBlockingFilter().filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Map<?, ?> body = objectMapper.readValue(exchange.getResponse().getBodyAsString().block(), Map.class);
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("errorCode")).isEqualTo("NOT_FOUND");
        assertThat(body.get("message")).isEqualTo("Kayıt bulunamadı.");
        assertThat(body.get("instance")).isEqualTo("/api/users/internal/profiles/1");
    }

    @Test
    void rateLimitRejection_shouldKeepRetryAfterAndMessage() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        RateLimitResponses.reject(exchange, 42).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("42");
        Map<?, ?> body = objectMapper.readValue(exchange.getResponse().getBodyAsString().block(), Map.class);
        assertThat(body.get("errorCode")).isEqualTo("RATE_LIMITED");
        assertThat((String) body.get("message")).contains("42 saniye");
        assertThat(body.get("detail")).isEqualTo(body.get("message"));
    }
}
