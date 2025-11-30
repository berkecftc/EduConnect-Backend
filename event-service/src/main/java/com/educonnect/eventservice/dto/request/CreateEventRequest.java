package com.educonnect.eventservice.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

public class CreateEventRequest {
    private String title;
    private String description;
    private LocalDateTime eventTime;
    private String location;
    private String clubName; // (Performans için opsiyonel)

    // Getter/Setter metotları...
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
}