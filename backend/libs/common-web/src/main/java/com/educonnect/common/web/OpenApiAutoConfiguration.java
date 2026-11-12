package com.educonnect.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springdoc.core.customizers.OpenApiCustomizer")
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", matchIfMissing = true)
public class OpenApiAutoConfiguration {

    static final String BEARER_SCHEME = "bearerAuth";
    static final String PROBLEM_SCHEMA = "Problem";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI educonnectOpenApi(Environment environment) {
        String application = environment.getProperty("spring.application.name", "educonnect");
        return new OpenAPI()
                .info(new Info()
                        .title(application)
                        .version("v1")
                        .description("EduConnect " + application + " API. Hatalar RFC 9457 ProblemDetail "
                                + "(application/problem+json) biçiminde; gösterilecek metin `message`, sabit kod `errorCode`."))
                .servers(List.of(new Server().url("/").description("API Gateway")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public OpenApiCustomizer internalEndpointsHidingCustomizer() {
        return openApi -> {
            if (openApi.getPaths() != null) {
                openApi.getPaths().keySet().removeIf(OpenApiAutoConfiguration::isInternalPath);
            }
        };
    }

    @Bean
    public OpenApiCustomizer problemResponseCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                if (operation.getResponses() != null && !operation.getResponses().containsKey("default")) {
                    operation.getResponses().addApiResponse("default", new ApiResponse()
                            .description("Hata (RFC 9457 ProblemDetail)")
                            .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    new io.swagger.v3.oas.models.media.MediaType()
                                            .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)))));
                }
            }));
        };
    }

    static Schema<?> problemSchema() {
        Schema<?> fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema());
        return new ObjectSchema()
                .description("Tüm hata yanıtları. Kullanıcıya `message` gösterilir, mantık `errorCode` ile kurulur.")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Not Found"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().example("Ders bulunamadı: 7f1c…"))
                .addProperty("instance", new StringSchema().example("/api/courses/7f1c…"))
                .addProperty("errorCode", new StringSchema().example("COURSE_NOT_FOUND"))
                .addProperty("message", new StringSchema().example("Ders bulunamadı: 7f1c…"))
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .addProperty("errors", new ArraySchema().items(fieldError)
                        .description("Yalnız doğrulama hatalarında (VALIDATION_FAILED)"));
    }

    static boolean isInternalPath(String path) {
        return path.contains("/internal/") || path.endsWith("/internal");
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.config.annotation.web.builders.HttpSecurity")
    static class ApiDocsSecurityConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain apiDocsSecurityFilterChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/v3/api-docs", "/v3/api-docs/**")
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET,
                            "/v3/api-docs", "/v3/api-docs/**").permitAll().anyRequest().denyAll());
            return http.build();
        }
    }
}
