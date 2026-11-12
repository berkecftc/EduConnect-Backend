package com.educonnect.clubservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.TopicExchange;
import com.educonnect.common.messaging.notification.NotificationRequest;

@Configuration
public class ClubRabbitMQConfig {

    public static final String USER_EXCHANGE_NAME = "user-exchange";

    public static final String CLUB_EXCHANGE_NAME = "club-exchange";

    public static final String USER_DELETED_QUEUE = "club-service.user.deleted";
    public static final String USER_DELETED_ROUTING_KEY = "user.delete";

    public static final String USER_AFFILIATION_STATUS_QUEUE = "club-service.user.affiliation-status";
    public static final String USER_AFFILIATION_STATUS_ROUTING_KEY = "user.affiliation.status";

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE_NAME);
    }

    @Bean
    public Queue userDeletedQueue() {
        return QueueBuilder.durable(USER_DELETED_QUEUE).build();
    }

    @Bean
    public Binding userDeletedBinding(Queue userDeletedQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userDeletedQueue).to(userExchange).with(USER_DELETED_ROUTING_KEY);
    }

    @Bean
    public Queue userAffiliationStatusQueue() {
        return QueueBuilder.durable(USER_AFFILIATION_STATUS_QUEUE).build();
    }

    @Bean
    public Binding userAffiliationStatusBinding(Queue userAffiliationStatusQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userAffiliationStatusQueue).to(userExchange).with(USER_AFFILIATION_STATUS_ROUTING_KEY);
    }

    @Bean
    public DirectExchange clubExchange() {
        return new DirectExchange(CLUB_EXCHANGE_NAME);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NotificationRequest.EXCHANGE);
    }
}
