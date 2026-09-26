package com.educonnect.apigateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdResponseFilterTest {

    private final GatewayFilterChain chain = exchange -> Mono.empty();

    @Test
    void filter_withTracedRequest_shouldExposeTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/clubs"));
        ServerRequestObservationContext observation = new ServerRequestObservationContext(
                exchange.getRequest(), exchange.getResponse(), exchange.getAttributes());
        TraceContext traceContext = mock(TraceContext.class);
        when(traceContext.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        Span span = mock(Span.class);
        when(span.context()).thenReturn(traceContext);
        TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
        tracingContext.setSpan(span);
        observation.put(TracingObservationHandler.TracingContext.class, tracingContext);
        exchange.getAttributes().put(ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observation);

        new TraceIdResponseFilter().filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdResponseFilter.HEADER))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void filter_withoutObservation_shouldNotAddHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/clubs"));

        new TraceIdResponseFilter().filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().containsKey(TraceIdResponseFilter.HEADER)).isFalse();
    }
}
