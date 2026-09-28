package com.educonnect.eventservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public class CreateEventRequest {
    @NotBlank(message = "Başlık boş olamaz")
    @Size(max = 255, message = "Başlık en fazla 255 karakter olabilir")
    private String title;
    private String description;
    @NotNull(message = "Etkinlik zamanı boş olamaz")
    private LocalDateTime eventTime;
    @Size(max = 255, message = "Konum en fazla 255 karakter olabilir")
    private String location;
    @NotBlank(message = "Kulüp adı boş olamaz")
    @Size(max = 255, message = "Kulüp adı en fazla 255 karakter olabilir")
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