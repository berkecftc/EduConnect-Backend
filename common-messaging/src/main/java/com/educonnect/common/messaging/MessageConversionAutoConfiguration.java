package com.educonnect.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
public class MessageConversionAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MessageConversionAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public JacksonJsonMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplateCustomizer unroutableMessageLogging() {
        return template -> {
            template.setMandatory(true);
            template.setReturnsCallback(MessageConversionAutoConfiguration::logReturned);
        };
    }

    static void logReturned(ReturnedMessage returned) {
        log.warn("Message was not routed to any queue: exchange={}, routingKey={}, replyCode={}, replyText={}",
                safe(returned.getExchange()), safe(returned.getRoutingKey()),
                returned.getReplyCode(), safe(returned.getReplyText()));
    }

    private static String safe(String value) {
        return value == null ? null : value.replaceAll("\\R", "_");
    }
}
