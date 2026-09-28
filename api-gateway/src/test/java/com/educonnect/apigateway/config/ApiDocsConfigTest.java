package com.educonnect.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocsConfigTest {

    @Test
    void swaggerUi_listsEveryDocumentedServiceThroughTheGatewayPrefix() {
        SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();

        new ApiDocsConfig(properties).registerServiceDocs();

        assertThat(properties.getUrls()).extracting(SwaggerUrl::getUrl)
                .containsExactlyElementsOf(ApiDocsConfig.DOCUMENTED_SERVICES.stream()
                        .map(service -> "/api-docs/" + service).toList());
        assertThat(properties.getUrlsPrimaryName()).isEqualTo("auth-services");
        assertThat(ApiDocsConfig.DOCUMENTED_SERVICES).doesNotContain("notification-service");
    }
}
