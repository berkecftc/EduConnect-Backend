package com.educonnect.userservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.educonnect.common.messaging.notification.NotificationRequest;
import org.springframework.amqp.core.TopicExchange;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_NAME = "user-profile-creation-queue";
    public static final String EXCHANGE_NAME = "user-exchange";
    public static final String ROUTING_KEY = "user-registration-key";

    // Academician message queue/routing key
    public static final String ACADEMICIAN_QUEUE_NAME = "academician-profile-create-queue";
    public static final String ACADEMICIAN_ROUTING_KEY = "profile.academician.create";

    // User delete queue/routing key
    public static final String USER_DELETE_QUEUE = "user-delete-queue";
    public static final String USER_DELETE_ROUTING_KEY = "user.delete";

    public static final String USER_EMAIL_CHANGED_QUEUE = "user-email-changed-queue";
    public static final String USER_EMAIL_CHANGED_ROUTING_KEY = "user.email.changed";

    public static final String USER_AFFILIATION_STATUS_QUEUE = "user-affiliation-status-queue";
    public static final String USER_AFFILIATION_STATUS_ROUTING_KEY = "user.affiliation.status";

    public static final String GAMIFICATION_EXCHANGE = "gamification.exchange";
    public static final String GAMIFICATION_PROFILE_COMPLETED_ROUTING_KEY = "gamification.user.profile_completed";


    @Bean
    public Queue userProfileCreationQueue() {
        return new Queue(QUEUE_NAME);
    }

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NotificationRequest.EXCHANGE);
    }

    @Bean
    public Queue academicianProfileCreationQueue() {
        return new Queue(ACADEMICIAN_QUEUE_NAME);
    }

    @Bean
    public Queue userDeleteQueue() {
        return new Queue(USER_DELETE_QUEUE);
    }

    @Bean
    public Binding binding(Queue userProfileCreationQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userProfileCreationQueue).to(userExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding academicianBinding(Queue academicianProfileCreationQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(academicianProfileCreationQueue).to(userExchange).with(ACADEMICIAN_ROUTING_KEY);
    }

    @Bean
    public Binding userDeleteBinding(Queue userDeleteQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userDeleteQueue).to(userExchange).with(USER_DELETE_ROUTING_KEY);
    }

    @Bean
    public Queue userEmailChangedQueue() {
        return new Queue(USER_EMAIL_CHANGED_QUEUE);
    }

    @Bean
    public Binding userEmailChangedBinding(Queue userEmailChangedQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userEmailChangedQueue).to(userExchange).with(USER_EMAIL_CHANGED_ROUTING_KEY);
    }

    @Bean
    public Queue userAffiliationStatusQueue() {
        return new Queue(USER_AFFILIATION_STATUS_QUEUE);
    }

    @Bean
    public Binding userAffiliationStatusBinding(Queue userAffiliationStatusQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userAffiliationStatusQueue).to(userExchange).with(USER_AFFILIATION_STATUS_ROUTING_KEY);
    }
}
