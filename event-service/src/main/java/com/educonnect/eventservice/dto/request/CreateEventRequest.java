package com.educonnect.eventservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class CreateEventRequest {
    @NotBlank(message = "Başlık boş olamaz")
    @Size(max = 255, message = "Başlık en fazla 255 karakter olabilir")
    private String title;
    private String description;
    @NotNull(message = "Etkinlik başlangıcı boş olamaz")
    @JsonAlias("eventTime")
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @Size(max = 2000, message = "Konuşmacı bilgisi en fazla 2000 karakter olabilir")
    private String speakers;
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
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public String getSpeakers() { return speakers; }
    public void setSpeakers(String speakers) { this.speakers = speakers; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
}
