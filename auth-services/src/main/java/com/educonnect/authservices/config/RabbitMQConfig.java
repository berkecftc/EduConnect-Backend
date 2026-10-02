package com.educonnect.authservices.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange ve Queue isimlerini sabit olarak tanımlarız.
    public static final String EXCHANGE_NAME = "user-exchange";
    public static final String QUEUE_NAME = "user-profile-creation-queue";
    public static final String ROUTING_KEY = "user-registration-key";

    // Academician queue/routing key (user-service ile uyumlu)
    public static final String ACADEMICIAN_QUEUE_NAME = "academician-profile-create-queue";
    public static final String ACADEMICIAN_ROUTING_KEY = "profile.academician.create";

    public static final String CLUB_MANAGEMENT_QUEUE = "user-club-management-queue";
    public static final String CLUB_MANAGEMENT_DLQ = CLUB_MANAGEMENT_QUEUE + ".dlq";
    public static final String CLUB_MANAGEMENT_ROUTING_KEY = "user.club-management.changed";

    // Kullanıcı silme için queue ve routing key
    public static final String USER_DELETE_QUEUE = "user-delete-queue";
    public static final String USER_DELETE_ROUTING_KEY = "user.delete";

    // Kullanıcı hesap durumu bildirimi için queue ve routing key (onay/red e-postası)
    public static final String USER_ACCOUNT_STATUS_QUEUE = "user-account-status-queue";
    public static final String USER_ACCOUNT_STATUS_ROUTING_KEY = "user.account.status";

    // Şifre sıfırlama için queue ve routing key
    public static final String PASSWORD_RESET_QUEUE = "password-reset-queue";
    public static final String PASSWORD_RESET_ROUTING_KEY = "user.password.reset";
    public static final String EMAIL_VERIFICATION_ROUTING_KEY = "user.email.verify";
    public static final String USER_EMAIL_CHANGED_ROUTING_KEY = "user.email.changed";
    public static final String USER_AFFILIATION_STATUS_ROUTING_KEY = "user.affiliation.status";

    public static final String GAMIFICATION_EXCHANGE = "gamification.exchange";
    public static final String GAMIFICATION_USER_LOGIN_ROUTING_KEY = "gamification.user.login";

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public TopicExchange gamificationExchange() {
        return new TopicExchange(GAMIFICATION_EXCHANGE);
    }

    @Bean
    public Queue userProfileCreationQueue() {
        return new Queue(QUEUE_NAME);
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
    public Queue userAccountStatusQueue() {
        return new Queue(USER_ACCOUNT_STATUS_QUEUE);
    }

    @Bean
    public Queue passwordResetQueue() {
        return new Queue(PASSWORD_RESET_QUEUE);
    }

    // Exchange ile Queue'yu routing key aracılığıyla birbirine bağlar.
    @Bean
    public Binding binding(Queue userProfileCreationQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userProfileCreationQueue).to(userExchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue clubManagementQueue() {
        return QueueBuilder.durable(CLUB_MANAGEMENT_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(CLUB_MANAGEMENT_DLQ)
                .build();
    }

    @Bean
    public Queue clubManagementDeadLetterQueue() {
        return QueueBuilder.durable(CLUB_MANAGEMENT_DLQ).build();
    }

    @Bean
    public Binding clubManagementBinding(Queue clubManagementQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(clubManagementQueue).to(userExchange).with(CLUB_MANAGEMENT_ROUTING_KEY);
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
    public Binding userAccountStatusBinding(Queue userAccountStatusQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userAccountStatusQueue).to(userExchange).with(USER_ACCOUNT_STATUS_ROUTING_KEY);
    }

    @Bean
    public Binding passwordResetBinding(Queue passwordResetQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(passwordResetQueue).to(userExchange).with(PASSWORD_RESET_ROUTING_KEY);
    }
}
