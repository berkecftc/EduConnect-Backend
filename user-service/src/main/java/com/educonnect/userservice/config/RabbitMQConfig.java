package com.educonnect.userservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_NAME = "user-profile-creation-queue";
    public static final String EXCHANGE_NAME = "user-exchange";
    public static final String ROUTING_KEY = "user-registration-key";

    // Academician message queue/routing key
    public static final String ACADEMICIAN_QUEUE_NAME = "academician-profile-create-queue";
    public static final String ACADEMICIAN_ROUTING_KEY = "profile.academician.create";

    @Value("${user.listener.auto-start:true}")
    private boolean listenerAutoStart;

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("*");
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put(
                "com.educonnect.authservices.dto.message.UserRegisteredMessage",
                com.educonnect.userservice.dto.message.UserRegisteredMessage.class
        );
        idClassMapping.put(
                "com.educonnect.authservices.dto.message.AcademicianProfileMessage",
                com.educonnect.userservice.dto.message.AcademicianProfileMessage.class
        );
        classMapper.setIdClassMapping(idClassMapping);
        converter.setClassMapper(classMapper);
        return converter;
    }

    @Bean
    public Queue userProfileCreationQueue() {
        return new Queue(QUEUE_NAME);
    }

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue academicianProfileCreationQueue() {
        return new Queue(ACADEMICIAN_QUEUE_NAME);
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
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                               MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAutoStartup(listenerAutoStart);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        return factory;
    }
}