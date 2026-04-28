package com.educonnect.apigateway.config;

import com.educonnect.apigateway.filter.AuthenticationFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programatik route tanımları.
 * Config repo'daki YAML tanımlarına ek olarak çalışır.
 */
@Configuration
public class AdditionalRoutesConfig {

    private final AuthenticationFilter authenticationFilter;

    public AdditionalRoutesConfig(AuthenticationFilter authenticationFilter) {
        this.authenticationFilter = authenticationFilter;
    }

    @Bean
    public RouteLocator additionalRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Akademisyen (Danışman) endpoint'leri -> club-service
                .route("academician-role-change-routes", r -> r
                        .path("/api/academician/**")
                        .filters(f -> f.filter(authenticationFilter.apply(new AuthenticationFilter.Config())))
                        .uri("lb://club-service"))
                // Gamification endpoint'leri -> gamification-service
                .route("gamification-routes", r -> r
                        .path("/api/gamification/**")
                        .filters(f -> f.filter(authenticationFilter.apply(new AuthenticationFilter.Config())))
                        .uri("lb://gamification-service"))
                // LLM / AI endpoint'leri -> llm-service
                .route("llm-routes", r -> r
                        // expose under /api/llm/** (rewritten to /api/ai/**) and /api/ai/** for backward compatibility
                        .path("/api/llm/**", "/api/ai/**")
                        .filters(f -> f
                                .filter(authenticationFilter.apply(new AuthenticationFilter.Config()))
                                // if clients call /api/llm/..., rewrite to the controller's /api/ai/... mapping
                                .rewritePath("/api/llm/(?<segment>.*)", "/api/ai/${segment}"))
                        .uri("lb://llm-service"))
                .build();
    }
}

