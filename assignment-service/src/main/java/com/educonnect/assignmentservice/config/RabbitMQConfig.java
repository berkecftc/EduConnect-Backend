package com.educonnect.assignmentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Dinleyeceğimiz Exchange (Course Service'deki ile AYNI İSİM olmalı)
    public static final String COURSE_EXCHANGE = "course.exchange";

    // Bizim oluşturacağımız Kuyruk
    public static final String ASSIGNMENT_QUEUE = "course.assignment.queue";

    // Hangi mesajları yakalayacağız? (Silinme olayları)
    public static final String ROUTING_KEY_DELETED = "course.deleted";

    // 1. Exchange Tanımla (Eğer Course Service oluşturmadıysa biz oluşturalım)
    @Bean
    public TopicExchange courseExchange() {
        return new TopicExchange(COURSE_EXCHANGE);
    }

    // 2. Kuyruk Tanımla
    @Bean
    public Queue assignmentQueue() {
        return new Queue(ASSIGNMENT_QUEUE, true); // Durable = true
    }

    // 3. Bağlama (Binding) Yap: Exchange -> Queue
    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_DELETED);
    }

    // JSON Dönüştürücü
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}