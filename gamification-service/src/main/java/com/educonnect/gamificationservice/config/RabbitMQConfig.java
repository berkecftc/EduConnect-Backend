package com.educonnect.gamificationservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
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

    public static final String GAMIFICATION_EXCHANGE = "gamification.exchange";
    public static final String GAMIFICATION_POINTS_QUEUE = "gamification.points.queue";
    public static final String GAMIFICATION_ROUTING_PATTERN = "gamification.*.*";

    public static final String USER_EXCHANGE = "user-exchange";
    public static final String USER_DELETED_QUEUE = "gamification-service.user.deleted";
    public static final String USER_DELETED_ROUTING_KEY = "user.delete";

    @Bean
    public TopicExchange gamificationExchange() {
        return new TopicExchange(GAMIFICATION_EXCHANGE);
    }

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE);
    }

    @Bean
    public Queue userDeletedQueue() {
        return new Queue(USER_DELETED_QUEUE, true);
    }

    @Bean
    public Binding userDeletedBinding(Queue userDeletedQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userDeletedQueue).to(userExchange).with(USER_DELETED_ROUTING_KEY);
    }

    @Bean
    public Queue gamificationPointsQueue() {
        return new Queue(GAMIFICATION_POINTS_QUEUE, true);
    }

    @Bean
    public Binding gamificationBinding(Queue gamificationPointsQueue, TopicExchange gamificationExchange) {
        return BindingBuilder.bind(gamificationPointsQueue)
                .to(gamificationExchange)
                .with(GAMIFICATION_ROUTING_PATTERN);
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


