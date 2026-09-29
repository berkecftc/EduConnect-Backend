package com.educonnect.eventservice.listener;

import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.ClubUpdateMessage;
import com.educonnect.eventservice.service.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ClubEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClubEventListener.class);

    private final EventService eventService;

    public ClubEventListener(EventService eventService) {
        this.eventService = eventService;
    }

    @RabbitListener(queues = EventRabbitMQConfig.DELETE_EVENTS_QUEUE)
    public void handleClubDeleted(ClubUpdateMessage message) {
        if (message == null || message.getClubId() == null) {
            throw new AmqpRejectAndDontRequeueException("Club delete message without club id");
        }
        log.info("Received club delete event for club {}", message.getClubId());
        eventService.deleteEventsByClubId(message.getClubId());
    }

    @RabbitListener(queues = EventRabbitMQConfig.UPDATE_CLUB_QUEUE)
    public void handleClubUpdated(ClubUpdateMessage message) {
        if (message == null || message.getClubId() == null) {
            throw new AmqpRejectAndDontRequeueException("Club update message without club id");
        }
        log.info("Received club update event for club {}", message.getClubId());
        eventService.updateClubInfoForEvents(message.getClubId(), message.getNewName());
    }
}
