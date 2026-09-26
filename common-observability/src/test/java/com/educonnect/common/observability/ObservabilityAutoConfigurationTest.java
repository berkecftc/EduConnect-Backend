package com.educonnect.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
            .withPropertyValues("spring.application.name=club-service");

    @Test
    void withoutEndpoint_shouldNotCreateExporter() {
        runner.run(context -> assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class));
    }

    @Test
    void withBlankEndpoint_shouldNotCreateExporter() {
        runner.withPropertyValues("educonnect.observability.otlp-endpoint=")
                .run(context -> assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class));
    }

    @Test
    void withEndpoint_shouldCreateExporter() {
        runner.withPropertyValues("educonnect.observability.otlp-endpoint=http://tempo:4318/v1/traces")
                .run(context -> assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void customizer_shouldTagMetricsWithApplicationName() {
        runner.run(context -> {
            MeterRegistry registry = new SimpleMeterRegistry();
            context.getBean(MeterRegistryCustomizer.class).customize(registry);
            registry.counter("educonnect.test").increment();

            assertThat(registry.get("educonnect.test").counter().getId().getTag("application")).isEqualTo("club-service");
        });
    }
}
