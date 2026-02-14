package com.educonnect.authservices.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQConfig.class);

    // Göndereceğimiz ve alacağımız mesajların JSON formatında olmasını sağlar.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Exchange ve Queue isimlerini sabit olarak tanımlarız.
    public static final String EXCHANGE_NAME = "user-exchange";
    public static final String QUEUE_NAME = "user-profile-creation-queue";
    public static final String ROUTING_KEY = "user-registration-key";

    // Academician queue/routing key (user-service ile uyumlu)
    public static final String ACADEMICIAN_QUEUE_NAME = "academician-profile-create-queue";
    public static final String ACADEMICIAN_ROUTING_KEY = "profile.academician.create";

    // Kulüp rolü atama için queue ve routing key
    public static final String ROLE_ASSIGNMENT_QUEUE = "user-role-assignment-queue";
    public static final String ROLE_ASSIGNMENT_ROUTING_KEY = "user.role.assign";

    // Kulüp rolü kaldırma için queue ve routing key
    public static final String ROLE_REVOKE_QUEUE = "user-role-revoke-queue";
    public static final String ROLE_REVOKE_ROUTING_KEY = "user.role.revoke";

    // Kullanıcı silme için queue ve routing key
    public static final String USER_DELETE_QUEUE = "user-delete-queue";
    public static final String USER_DELETE_ROUTING_KEY = "user.delete";

    // Kullanıcı hesap durumu bildirimi için queue ve routing key (onay/red e-postası)
    public static final String USER_ACCOUNT_STATUS_QUEUE = "user-account-status-queue";
    public static final String USER_ACCOUNT_STATUS_ROUTING_KEY = "user.account.status";

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue userProfileCreationQueue() {
        return new Queue(QUEUE_NAME);
    }

    @Bean
    public Queue userRoleAssignmentQueue() {
        return new Queue(ROLE_ASSIGNMENT_QUEUE);
    }

    @Bean
    public Queue userRoleRevokeQueue() {
        return new Queue(ROLE_REVOKE_QUEUE);
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

    // Exchange ile Queue'yu routing key aracılığıyla birbirine bağlar.
    @Bean
    public Binding binding(Queue userProfileCreationQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userProfileCreationQueue).to(userExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding roleAssignmentBinding(Queue userRoleAssignmentQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userRoleAssignmentQueue).to(userExchange).with(ROLE_ASSIGNMENT_ROUTING_KEY);
    }

    @Bean
    public Binding roleRevokeBinding(Queue userRoleRevokeQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userRoleRevokeQueue).to(userExchange).with(ROLE_REVOKE_ROUTING_KEY);
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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        // Unroutable mesajlar icin geri bildirim al
        template.setMandatory(true);
        template.setReturnsCallback(returned -> {
            LOGGER.warn("Rabbit RETURN: routing failed. exchange={}, routingKey={}, replyCode={}, replyText={}, correlationId={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText(),
                    returned.getMessage().getMessageProperties().getCorrelationId());
        });
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                LOGGER.warn("Rabbit CONFIRM: NACK publish. correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : null, cause);
            } else {
                LOGGER.debug("Rabbit CONFIRM: ACK publish. correlationId={}",
                        correlationData != null ? correlationData.getId() : null);
            }
        });
        return template;
    }
}