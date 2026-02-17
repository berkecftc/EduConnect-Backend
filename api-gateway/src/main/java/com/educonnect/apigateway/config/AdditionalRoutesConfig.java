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
                .build();
    }
}

