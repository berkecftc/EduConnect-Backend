package com.educonnect.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiAutoConfigurationTest {

    private final OpenApiAutoConfiguration configuration = new OpenApiAutoConfiguration();

    @Test
    void openApi_usesApplicationNameAndBearerScheme() {
        OpenAPI openApi = configuration.educonnectOpenApi(
                new MockEnvironment().withProperty("spring.application.name", "club-service"));

        assertThat(openApi.getInfo().getTitle()).isEqualTo("club-service");
        assertThat(openApi.getServers()).singleElement().extracting("url").isEqualTo("/");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey(OpenApiAutoConfiguration.BEARER_SCHEME);
        assertThat(openApi.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey(OpenApiAutoConfiguration.BEARER_SCHEME));
    }

    @Test
    void customizer_hidesInternalEndpoints() {
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/clubs", new PathItem())
                .addPathItem("/api/clubs/internal/catalog", new PathItem())
                .addPathItem("/api/users/internal/profiles/batch", new PathItem())
                .addPathItem("/api/auth/internal", new PathItem())
                .addPathItem("/api/courses/{courseId}/file", new PathItem()));

        configuration.internalEndpointsHidingCustomizer().customise(openApi);

        assertThat(openApi.getPaths().keySet()).containsExactlyInAnyOrder("/api/clubs", "/api/courses/{courseId}/file");
    }

    @Test
    void problemCustomizer_addsProblemSchemaAsDefaultErrorResponse() {
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()));
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/api/clubs", new PathItem().get(operation)));

        configuration.problemResponseCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas().get(OpenApiAutoConfiguration.PROBLEM_SCHEMA).getProperties())
                .containsKeys("status", "detail", "errorCode", "message", "errors");
        ApiResponse fallback = operation.getResponses().get("default");
        assertThat(fallback.getContent()).containsKey("application/problem+json");
        assertThat(fallback.getContent().get("application/problem+json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/Problem");
        assertThat(operation.getResponses()).containsKey("200");
    }
}
