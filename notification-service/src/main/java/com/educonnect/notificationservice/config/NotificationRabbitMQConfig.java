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
    public static final String COURSE_EXCHANGE_NAME = "course.exchange"; // Course service bunu kullanıyor

    // --- BU SERVİSİN KENDİ KUYRUKLARI ---
    public static final String NOTIFICATION_EVENT_QUEUE = "notification-event-created-queue";

    // --- DERS DUYURU VE ÖDEV BİLDİRİM KUYRUKLARI ---
    public static final String COURSE_ANNOUNCEMENT_QUEUE = "notification-course-announcement-queue";
    public static final String COURSE_ASSIGNMENT_QUEUE = "notification-course-assignment-queue";

    // --- KULLANICI HESAP DURUMU BİLDİRİMİ ---
    public static final String USER_ACCOUNT_STATUS_QUEUE = "user-account-status-queue";
    public static final String USER_ACCOUNT_STATUS_ROUTING_KEY = "user.account.status";

    // --- ŞİFRE SIFIRLAMA BİLDİRİMİ ---
    public static final String PASSWORD_RESET_QUEUE = "password-reset-queue";
    public static final String PASSWORD_RESET_ROUTING_KEY = "user.password.reset";

    // --- ROUTING KEY'LER ---
    public static final String ROUTING_KEY_EVENT_CREATED = "event.created";
    public static final String ROUTING_KEY_COURSE_ANNOUNCEMENT = "course.announcement.created";
    public static final String ROUTING_KEY_COURSE_ASSIGNMENT = "course.assignment.created";

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

    // 2. Exchange'i Tanımla
    @Bean
    public DirectExchange clubExchange() {
        return new DirectExchange(CLUB_EXCHANGE_NAME);
    }

    // 3. Bağlama (Binding)
    @Bean
    public Binding bindingEventCreated(Queue notificationEventQueue, DirectExchange clubExchange) {
        return BindingBuilder.bind(notificationEventQueue).to(clubExchange).with(ROUTING_KEY_EVENT_CREATED);
    }

    // --- COURSE EXCHANGE TANIMLARI ---
    @Bean
    public TopicExchange courseExchange() {
        return new TopicExchange(COURSE_EXCHANGE_NAME);
    }

    @Bean
    public Queue courseAnnouncementQueue() {
        return new Queue(COURSE_ANNOUNCEMENT_QUEUE, true);
    }

    @Bean
    public Queue courseAssignmentQueue() {
        return new Queue(COURSE_ASSIGNMENT_QUEUE, true);
    }

    @Bean
    public Binding bindingCourseAnnouncement(
            @Qualifier("courseAnnouncementQueue") Queue courseAnnouncementQueue,
            @Qualifier("courseExchange") TopicExchange courseExchange) {
        return BindingBuilder.bind(courseAnnouncementQueue).to(courseExchange).with(ROUTING_KEY_COURSE_ANNOUNCEMENT);
    }

    @Bean
    public Binding bindingCourseAssignment(
            @Qualifier("courseAssignmentQueue") Queue courseAssignmentQueue,
            @Qualifier("courseExchange") TopicExchange courseExchange) {
        return BindingBuilder.bind(courseAssignmentQueue).to(courseExchange).with(ROUTING_KEY_COURSE_ASSIGNMENT);
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
    public Queue passwordResetQueue() {
        return new Queue(PASSWORD_RESET_QUEUE);
    }

    @Bean
    public Binding bindingUserAccountStatus(
            @Qualifier("userAccountStatusQueue") Queue userAccountStatusQueue,
            @Qualifier("userExchange") DirectExchange userExchange) {
        return BindingBuilder.bind(userAccountStatusQueue).to(userExchange).with(USER_ACCOUNT_STATUS_ROUTING_KEY);
    }

    @Bean
    public Binding bindingPasswordReset(
            @Qualifier("passwordResetQueue") Queue passwordResetQueue,
            @Qualifier("userExchange") DirectExchange userExchange) {
        return BindingBuilder.bind(passwordResetQueue).to(userExchange).with(PASSWORD_RESET_ROUTING_KEY);
    }
}