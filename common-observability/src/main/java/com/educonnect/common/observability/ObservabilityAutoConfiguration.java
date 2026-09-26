package com.educonnect.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> educonnectApplicationTag(Environment environment) {
        String application = environment.getProperty("spring.application.name", "unknown");
        return registry -> registry.config().commonTags("application", application);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OtlpHttpSpanExporter.class)
    @Conditional(OtlpEndpointConfiguredCondition.class)
    static class OtlpExportConfiguration {

        @Bean(destroyMethod = "close")
        public OtlpHttpSpanExporter educonnectOtlpSpanExporter(ObservabilityProperties properties) {
            return OtlpHttpSpanExporter.builder()
                    .setEndpoint(properties.otlpEndpoint())
                    .setTimeout(properties.otlpTimeout())
                    .build();
        }
    }
}
