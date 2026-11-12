package com.educonnect.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorAttributesTest {

    @Test
    void serviceUnavailable_shouldCarryTurkishMessageAndErrorCode() {
        GatewayErrorAttributes errorAttributes = new GatewayErrorAttributes();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/clubs").build());
        ServerRequest request = ServerRequest.create(exchange, ServerCodecConfigurer.create().getReaders());
        errorAttributes.storeErrorInformation(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to find instance for club-service"), exchange);

        Map<String, Object> attributes = errorAttributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());

        assertThat(attributes.get("status")).isEqualTo(503);
        assertThat(attributes.get("errorCode")).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(attributes.get("message")).isEqualTo("Servis şu an kullanılamıyor. Lütfen daha sonra tekrar deneyin.");
        assertThat(attributes).doesNotContainKey("trace");
    }

    @Test
    void unresolvableUpstream_shouldBecomeServiceUnavailable() {
        GatewayErrorAttributes errorAttributes = new GatewayErrorAttributes();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/clubs").build());
        ServerRequest request = ServerRequest.create(exchange, ServerCodecConfigurer.create().getReaders());
        errorAttributes.storeErrorInformation(new IllegalStateException("upstream",
                new UnknownHostException("Failed to resolve club-service 172.18.0.9")), exchange);

        Map<String, Object> attributes = errorAttributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());

        assertThat(attributes.get("status")).isEqualTo(503);
        assertThat(attributes.get("errorCode")).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(attributes.toString()).doesNotContain("172.18.0.9");
    }
}
