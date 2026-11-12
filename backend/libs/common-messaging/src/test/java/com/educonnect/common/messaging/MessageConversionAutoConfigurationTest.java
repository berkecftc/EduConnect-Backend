package com.educonnect.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MessageConversionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessageConversionAutoConfiguration.class, RabbitAutoConfiguration.class));

    @Test
    void theRabbitTemplateSendsJsonAndReportsUnroutableMessages() {
        runner.run(context -> {
            RabbitTemplate template = context.getBean(RabbitTemplate.class);
            assertThat(template.getMessageConverter()).isInstanceOf(JacksonJsonMessageConverter.class);
            assertThat(template.isMandatoryFor(null)).isTrue();
        });
    }

    @Test
    void aServiceCanStillProvideItsOwnConverter() {
        runner.withUserConfiguration(CustomConverter.class).run(context -> {
            assertThat(context).hasSingleBean(MessageConverter.class);
            assertThat(context.getBean(RabbitTemplate.class).getMessageConverter()).isInstanceOf(SimpleMessageConverter.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomConverter {

        @Bean
        MessageConverter messageConverter() {
            return new SimpleMessageConverter();
        }
    }
}
