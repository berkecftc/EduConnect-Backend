package com.educonnect.llmservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String POST_MODERATION_EXCHANGE = "post.moderation.exchange";
    public static final String POST_MODERATION_ROUTING_KEY = "post.moderation.pending";
    public static final String POST_MODERATION_LLM_QUEUE = "post.moderation.llm.queue";
    public static final String POST_MODERATION_REVIEW_QUEUE = "post.moderation.review.queue";

    @Bean
    public Queue postModerationReviewQueue() {
        return new Queue(POST_MODERATION_REVIEW_QUEUE, true);
    }

    @Bean
    public TopicExchange postModerationExchange() {
        return new TopicExchange(POST_MODERATION_EXCHANGE);
    }

    @Bean
    public Queue postModerationLlmQueue() {
        return new Queue(POST_MODERATION_LLM_QUEUE, true);
    }

    @Bean
    public Binding postModerationBinding(Queue postModerationLlmQueue, TopicExchange postModerationExchange) {
        return BindingBuilder
                .bind(postModerationLlmQueue)
                .to(postModerationExchange)
                .with(POST_MODERATION_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(4);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}

