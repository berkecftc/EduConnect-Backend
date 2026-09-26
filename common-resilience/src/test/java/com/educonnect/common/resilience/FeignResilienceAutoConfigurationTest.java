package com.educonnect.common.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FeignResilienceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeignResilienceAutoConfiguration.class));

    @Test
    void defaults_shouldRegisterCapabilityWithDefaultThresholds() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FeignCircuitBreakerCapability.class);
            var config = context.getBean(FeignCircuitBreakerCapability.class).registry().getDefaultConfig();
            assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(config.getSlidingWindowSize()).isEqualTo(20);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
        });
    }

    @Test
    void properties_shouldOverrideThresholds() {
        runner.withPropertyValues(
                        "educonnect.resilience.feign.circuit-breaker.failure-rate-threshold=30",
                        "educonnect.resilience.feign.circuit-breaker.wait-duration-in-open-state=5s")
                .run(context -> {
                    var config = context.getBean(FeignCircuitBreakerCapability.class).registry().getDefaultConfig();
                    assertThat(config.getFailureRateThreshold()).isEqualTo(30f);
                    assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(Duration.ofSeconds(5).toMillis());
                });
    }

    @Test
    void disabled_shouldNotRegisterCapability() {
        runner.withPropertyValues("educonnect.resilience.feign.circuit-breaker.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FeignCircuitBreakerCapability.class));
    }
}
