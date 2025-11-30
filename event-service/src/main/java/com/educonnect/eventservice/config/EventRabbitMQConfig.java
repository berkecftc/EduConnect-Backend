package com.educonnect.eventservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventRabbitMQConfig {

    // club-service'te tanımladığımız Exchange adı (AYNI OLMALI)
    public static final String CLUB_EXCHANGE_NAME = "club-exchange";

    // Bu servisin dinleyeceği kuyruk
    public static final String DELETE_EVENTS_QUEUE = "delete-events-queue";

    // club-service'in gönderdiği routing key (AYNI OLMALI)
    public static final String ROUTING_KEY_CLUB_DELETED = "club.deleted";

    public static final String ROUTING_KEY_EVENT_CREATED = "event.created";

    // YENİ SABİTLER
    public static final String UPDATE_CLUB_QUEUE = "update-club-info-queue";
    public static final String ROUTING_KEY_CLUB_UPDATED = "club.updated";

    @Bean
    public DirectExchange clubExchange() {
        return new DirectExchange(CLUB_EXCHANGE_NAME);
    }

    @Bean
    public Queue deleteEventsQueue() {
        return new Queue(DELETE_EVENTS_QUEUE);
    }

    @Bean
    public Binding bindingDeleteEvents(Queue deleteEventsQueue, DirectExchange clubExchange) {
        return BindingBuilder.bind(deleteEventsQueue).to(clubExchange).with(ROUTING_KEY_CLUB_DELETED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 1. YENİ KUYRUK: Güncelleme mesajları için
    @Bean
    public Queue updateClubQueue() {
        return new Queue(UPDATE_CLUB_QUEUE);
    }

    // 2. YENİ BAĞLAMA: club.updated mesajlarını bu kuyruğa yönlendir
    @Bean
    public Binding bindingUpdateClub(Queue updateClubQueue, DirectExchange clubExchange) {
        return BindingBuilder.bind(updateClubQueue).to(clubExchange).with(ROUTING_KEY_CLUB_UPDATED);
    }
}