package com.educonnect.eventservice.dto.request;

import java.util.UUID;

/**
 * Etkinlik katılım isteği oluşturma DTO'su
 */
public class CreateParticipationRequestDTO {

    private UUID eventId;
    private String message; // Opsiyonel mesaj

    public CreateParticipationRequestDTO() {}

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

