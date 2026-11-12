package com.educonnect.apigateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
public class TraceIdResponseFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        traceId(exchange).ifPresent(id -> exchange.getResponse().getHeaders().set(HEADER, id));
        return chain.filter(exchange);
    }

    static Optional<String> traceId(ServerWebExchange exchange) {
        return ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                .map(context -> context.<TracingObservationHandler.TracingContext>get(TracingObservationHandler.TracingContext.class))
                .map(TracingObservationHandler.TracingContext::getSpan)
                .map(Span::context)
                .map(context -> context.traceId());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
