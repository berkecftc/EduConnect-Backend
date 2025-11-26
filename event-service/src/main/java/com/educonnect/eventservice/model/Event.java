package com.educonnect.eventservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime eventTime; // Tarih ve Saat

    private String location; // Yer

    private String imageUrl; // Afiş (MinIO URL)

    @Column(nullable = false)
    private UUID clubId; // Hangi kulüp düzenliyor?

    @Column(nullable = false)
    private String clubName; // Performans için kulüp adını da burada tutabiliriz (Denormalizasyon)

    // Etkinlik durumu (ACTIVE, CANCELLED, COMPLETED)
    @Enumerated(EnumType.STRING)
    private EventStatus status = EventStatus.ACTIVE;

    // --- Getter & Setter (Lombok yoksa manuel ekleyin) ---
    public Event() {}

    // ... Getter ve Setter metotları ...
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
}