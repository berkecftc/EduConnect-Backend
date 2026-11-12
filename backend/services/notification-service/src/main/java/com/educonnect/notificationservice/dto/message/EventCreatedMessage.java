package com.educonnect.notificationservice.dto.message;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class EventCreatedMessage implements Serializable {
    private UUID eventId;
    private String title;
    private String description;
    private LocalDateTime eventTime;
    private String location;
    private UUID clubId;
    private String clubName;

    public EventCreatedMessage() {}

    // Getter ve Setter Metotları
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
}