package com.educonnect.llmservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Declarables;
import java.util.List;

@Configuration
public class RabbitMQConfig {

    public static final String POST_MODERATION_EXCHANGE = "post.moderation.exchange";
    public static final String POST_MODERATION_ROUTING_KEY = "post.moderation.pending";
    public static final String POST_MODERATION_LLM_QUEUE = "post.moderation.llm.queue";
    public static final String POST_MODERATION_REVIEW_QUEUE = "post.moderation.review.queue";
    public static final String CLUB_EXCHANGE = "club-exchange";
    public static final String CLUB_CATALOG_QUEUE = "llm-service.club-catalog";
    static final List<String> CLUB_CATALOG_ROUTING_KEYS = List.of("club.catalog.changed", "club.updated", "club.deleted");

    @Bean
    public DirectExchange clubExchange() {
        return new DirectExchange(CLUB_EXCHANGE);
    }

    @Bean
    public Queue clubCatalogQueue() {
        return new Queue(CLUB_CATALOG_QUEUE, true);
    }

    @Bean
    public Declarables clubCatalogBindings(Queue clubCatalogQueue, DirectExchange clubExchange) {
        return new Declarables(CLUB_CATALOG_ROUTING_KEYS.stream()
                .map(key -> BindingBuilder.bind(clubCatalogQueue).to(clubExchange).with(key))
                .toList());
    }

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
}
