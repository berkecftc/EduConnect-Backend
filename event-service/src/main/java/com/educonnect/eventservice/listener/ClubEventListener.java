package com.educonnect.eventservice.listener;

import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.ClubUpdateMessage;
import com.educonnect.eventservice.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ClubEventListener {

    private final EventService eventService;

    @RabbitListener(queues = EventRabbitMQConfig.DELETE_EVENTS_QUEUE)
    public void handleClubDeleted(String clubIdString) {
        // Mesaj genellikle tırnak içinde gelebilir, temizleyelim
        String cleanId = clubIdString.replace("\"", "");

        try {
            UUID clubId = UUID.fromString(cleanId);
            System.out.println("Received club delete event for ID: " + clubId);

            // Servis katmanındaki silme işlemini tetikle
            eventService.deleteEventsByClubId(clubId);

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID received from RabbitMQ: " + clubIdString);
        }
    }

    // --- YENİ GÜNCELLEME DİNLEYİCİSİ ---
    @RabbitListener(queues = EventRabbitMQConfig.UPDATE_CLUB_QUEUE)
    public void handleClubUpdated(ClubUpdateMessage message) {
        System.out.println("Received club update event: " + message.getNewName());

        try {
            // Servis katmanındaki güncelleme metodunu çağır
            eventService.updateClubInfoForEvents(message.getClubId(), message.getNewName());

        } catch (Exception e) {
            System.err.println("Error processing club update: " + e.getMessage());
        }
    }
}