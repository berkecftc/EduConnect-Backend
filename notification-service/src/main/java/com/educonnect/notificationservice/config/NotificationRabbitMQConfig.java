package com.educonnect.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitMQConfig {

    // --- DİNLENECEK EXCHANGE İSİMLERİ (Diğer servislerle aynı olmalı) ---
    public static final String CLUB_EXCHANGE_NAME = "club-exchange"; // Event service bunu kullanıyor
    public static final String USER_PROFILE_EXCHANGE = "user-profile-exchange"; // Auth service bunu kullanıyor

    // --- BU SERVİSİN KENDİ KUYRUKLARI ---
    public static final String NOTIFICATION_EVENT_QUEUE = "notification-event-created-queue";
    // (İleride şifre değişimi vb. için başka kuyruklar eklenebilir)

    // --- ROUTING KEY'LER ---
    public static final String ROUTING_KEY_EVENT_CREATED = "event.created";

    public static final String NOTIFICATION_REGISTRATION_QUEUE = "notification-registration-queue";
    public static final String ROUTING_KEY_EVENT_REGISTERED = "event.registered";

    @Bean
    public Queue notificationRegistrationQueue() {
        return new Queue(NOTIFICATION_REGISTRATION_QUEUE);
    }

    @Bean
    public Binding bindingEventRegistered(Queue notificationRegistrationQueue, DirectExchange clubExchange) {
        return BindingBuilder.bind(notificationRegistrationQueue).to(clubExchange).with(ROUTING_KEY_EVENT_REGISTERED);
    }

    // 1. Kuyruğu Tanımla
    @Bean
    public Queue notificationEventQueue() {
        return new Queue(NOTIFICATION_EVENT_QUEUE);
    }

    // 2. Exchange'i Tanımla (Eğer diğer servis henüz oluşturmadıysa diye garanti olsun)
    @Bean
    public DirectExchange clubExchange() {
        return new DirectExchange(CLUB_EXCHANGE_NAME);
    }

    // 3. Bağlama (Binding)
    // "club-exchange"e "event.created" anahtarıyla gelen mesajları "notification-event-queue"ya at.
    @Bean
    public Binding bindingEventCreated(Queue notificationEventQueue, DirectExchange clubExchange) {
        return BindingBuilder.bind(notificationEventQueue).to(clubExchange).with(ROUTING_KEY_EVENT_CREATED);
    }

    // JSON Dönüştürücü
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}