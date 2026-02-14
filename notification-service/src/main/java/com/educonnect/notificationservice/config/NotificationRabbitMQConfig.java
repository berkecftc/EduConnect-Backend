package com.educonnect.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitMQConfig {

    // --- DİNLENECEK EXCHANGE İSİMLERİ (Diğer servislerle aynı olmalı) ---
    public static final String CLUB_EXCHANGE_NAME = "club-exchange"; // Event service bunu kullanıyor
    public static final String USER_PROFILE_EXCHANGE = "user-profile-exchange"; // Auth service bunu kullanıyor
    public static final String USER_EXCHANGE_NAME = "user-exchange"; // Auth service user-exchange

    // --- BU SERVİSİN KENDİ KUYRUKLARI ---
    public static final String NOTIFICATION_EVENT_QUEUE = "notification-event-created-queue";
    // (İleride şifre değişimi vb. için başka kuyruklar eklenebilir)

    // --- KULLANICI HESAP DURUMU BİLDİRİMİ ---
    public static final String USER_ACCOUNT_STATUS_QUEUE = "user-account-status-queue";
    public static final String USER_ACCOUNT_STATUS_ROUTING_KEY = "user.account.status";

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

    // --- USER EXCHANGE TANIMLARI ---
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE_NAME);
    }

    @Bean
    public Queue userAccountStatusQueue() {
        return new Queue(USER_ACCOUNT_STATUS_QUEUE);
    }

    @Bean
    public Binding bindingUserAccountStatus(
            @Qualifier("userAccountStatusQueue") Queue userAccountStatusQueue,
            @Qualifier("userExchange") DirectExchange userExchange) {
        return BindingBuilder.bind(userAccountStatusQueue).to(userExchange).with(USER_ACCOUNT_STATUS_ROUTING_KEY);
    }
}