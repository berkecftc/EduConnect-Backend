package com.educonnect.courseservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String COURSE_EXCHANGE = "course.exchange";
    public static final String USER_EXCHANGE = "user-exchange";
    public static final String USER_DELETED_QUEUE = "course-service.user.deleted";
    public static final String USER_DELETED_ROUTING_KEY = "user.delete";
    public static final String ROUTING_KEY_DELETED = "course.deleted";
    public static final String ROUTING_KEY_ANNOUNCEMENT = "course.announcement.created";
    public static final String ROUTING_KEY_ASSIGNMENT_CREATED = "course.assignment.created";

    @Bean
    public TopicExchange courseExchange() {
        return new TopicExchange(COURSE_EXCHANGE);
    }

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE);
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
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}