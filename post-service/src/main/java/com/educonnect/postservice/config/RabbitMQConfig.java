package com.educonnect.postservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Post moderasyonu için RabbitMQ altyapı konfigürasyonu.
 *
 * TopicExchange tercih edilme sebebi:
 * - İleride farklı routing key pattern'leri (post.moderation.ai, post.moderation.manual)
 *   ile genişletilebilir; DirectExchange buna imkân tanımaz.
 * - Mevcut projede course.exchange de TopicExchange kullanıyor — tutarlılık sağlanır.
 *
 * Kuyruk durable: true → RabbitMQ yeniden başlatılsa bile mesajlar korunur (Mesaj Kaybı önlemi).
 */
@Configuration
public class RabbitMQConfig {

    public static final String POST_MODERATION_EXCHANGE = "post.moderation.exchange";
    public static final String POST_MODERATION_QUEUE = "post.moderation.queue";
    public static final String POST_MODERATION_ROUTING_KEY = "post.moderation.pending";

    @Bean
    public TopicExchange postModerationExchange() {
        return new TopicExchange(POST_MODERATION_EXCHANGE);
    }

    @Bean
    public Queue postModerationQueue() {
        // durable: true — broker yeniden başlatılsa bile kuyruk ve mesajları korunur
        return new Queue(POST_MODERATION_QUEUE, true);
    }

    @Bean
    public Binding postModerationBinding(Queue postModerationQueue, TopicExchange postModerationExchange) {
        return BindingBuilder
                .bind(postModerationQueue)
                .to(postModerationExchange)
                .with(POST_MODERATION_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

