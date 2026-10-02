package com.educonnect.eventservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"eventId", "studentId"}))
public class EventRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID studentId;

    @Column(nullable = false, unique = true)
    private String qrCode; // QR Kod içeriği (Benzersiz bir string)

    private LocalDateTime registrationTime = LocalDateTime.now();

    private boolean attended = false; // Kulüp yetkilisi okutunca true olacak

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // --- Getter & Setter ---
    public EventRegistration() {}

    // ... Getter ve Setter metotları ...
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public LocalDateTime getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(LocalDateTime registrationTime) { this.registrationTime = registrationTime; }
    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }

    public RegistrationStatus getStatus() { return status; }
    public void setStatus(RegistrationStatus status) { this.status = status; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public boolean isActive() {
        return status == RegistrationStatus.REGISTERED;
    }
}
