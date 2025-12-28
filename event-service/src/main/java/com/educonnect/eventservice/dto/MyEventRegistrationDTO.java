package com.educonnect.eventservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class MyEventRegistrationDTO {
    private UUID eventId;
    private String eventTitle;
    private String eventDescription;
    private LocalDateTime eventDate;
    private String eventLocation;
    private String qrCode;
    private LocalDateTime registrationTime;
    private boolean attended;

    public MyEventRegistrationDTO() {}

    // Getters and Setters
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getEventDescription() { return eventDescription; }
    public void setEventDescription(String eventDescription) { this.eventDescription = eventDescription; }

    public LocalDateTime getEventDate() { return eventDate; }
    public void setEventDate(LocalDateTime eventDate) { this.eventDate = eventDate; }

    public String getEventLocation() { return eventLocation; }
    public void setEventLocation(String eventLocation) { this.eventLocation = eventLocation; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public LocalDateTime getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(LocalDateTime registrationTime) { this.registrationTime = registrationTime; }

    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }
}

