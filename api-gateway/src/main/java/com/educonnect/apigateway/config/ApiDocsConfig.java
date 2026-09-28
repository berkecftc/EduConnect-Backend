package com.educonnect.apigateway.config;

import jakarta.annotation.PostConstruct;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class ApiDocsConfig {

    static final String DOCS_PREFIX = "/api-docs/";

    static final List<String> DOCUMENTED_SERVICES = List.of(
            "auth-services",
            "user-service",
            "club-service",
            "event-service",
            "course-service",
            "assignment-service",
            "post-service",
            "gamification-service",
            "llm-service");

    private final SwaggerUiConfigProperties swaggerUiConfigProperties;

    public ApiDocsConfig(SwaggerUiConfigProperties swaggerUiConfigProperties) {
        this.swaggerUiConfigProperties = swaggerUiConfigProperties;
    }

    @PostConstruct
    void registerServiceDocs() {
        Set<SwaggerUrl> urls = new LinkedHashSet<>();
        for (String service : DOCUMENTED_SERVICES) {
            urls.add(new SwaggerUrl(service, DOCS_PREFIX + service, service));
        }
        swaggerUiConfigProperties.setUrls(urls);
        swaggerUiConfigProperties.setUrlsPrimaryName(DOCUMENTED_SERVICES.get(0));
    }

    @Bean
    public RouteLocator apiDocsRoutes(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (String service : DOCUMENTED_SERVICES) {
            routes.route("api-docs-" + service, r -> r
                    .path(DOCS_PREFIX + service)
                    .filters(f -> f.setPath("/v3/api-docs"))
                    .uri("lb://" + service));
        }
        return routes.build();
    }
}
