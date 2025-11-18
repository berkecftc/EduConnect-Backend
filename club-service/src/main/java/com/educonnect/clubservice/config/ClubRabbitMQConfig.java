package com.educonnect.clubservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClubRabbitMQConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClubRabbitMQConfig.class);

    /**
     * Mesajların gönderileceği ana exchange (auth-services ile aynı isim olmalı)
     */
    public static final String EXCHANGE_NAME = "user-exchange";

    /**
     * Bu servisin 'club-exchange' adında bir DirectExchange kullanacağını Spring'e bildirir.
     * @return DirectExchange bean'i
     */
    @Bean
    public DirectExchange clubExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    /**
     * RabbitMQ mesajlarını (örn: UUID içeren mesajlar) Java nesnelerinden
     * JSON formatına ve tam tersine dönüştürmek için bir MessageConverter bean'i oluşturur.
     * Bu, RabbitTemplate'in .convertAndSend() metodunun JSON kullanmasını sağlar.
     * @return Jackson2JsonMessageConverter bean'i
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Teşhis amaçlı publish confirm ve return callback'leri ekleyelim
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        template.setReturnsCallback(returned -> {
            LOGGER.warn("Rabbit RETURN (unroutable): exchange={}, routingKey={}, replyCode={}, replyText={}, correlationId={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText(),
                    returned.getMessage().getMessageProperties().getCorrelationId());
        });
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                LOGGER.warn("Rabbit CONFIRM NACK: correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : null, cause);
            } else {
                LOGGER.debug("Rabbit CONFIRM ACK: correlationId={}",
                        correlationData != null ? correlationData.getId() : null);
            }
        });
        return template;
    }
}