package com.educonnect.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServiceClientAutoConfiguration.class));

    @Test
    void registersSingleTokenProviderAndInterceptor() {
        contextRunner
                .withPropertyValues("spring.application.name=event-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(ServiceTokenProvider.class);
                    assertThat(context).hasSingleBean(ServiceTokenHttpRequestInterceptor.class);
                    ServiceClientProperties properties = context.getBean(ServiceClientProperties.class);
                    assertThat(properties.tokenUri()).isEqualTo(ServiceClientProperties.DEFAULT_TOKEN_URI);
                });
    }

    @Test
    void bindsConfiguredClientProperties() {
        contextRunner
                .withPropertyValues(
                        "educonnect.security.service-client.id=custom-client",
                        "educonnect.security.service-client.secret=s3cret",
                        "educonnect.security.service-client.timeout=2s")
                .run(context -> {
                    ServiceClientProperties properties = context.getBean(ServiceClientProperties.class);
                    assertThat(properties.id()).isEqualTo("custom-client");
                    assertThat(properties.secret()).isEqualTo("s3cret");
                    assertThat(properties.timeout()).hasSeconds(2);
                });
    }
}
