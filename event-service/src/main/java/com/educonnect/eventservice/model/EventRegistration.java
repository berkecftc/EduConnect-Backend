package com.educonnect.eventservice.model;

import jakarta.persistence.*;
import java.time.Instant;
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

    private Instant registrationTime = Instant.now();

    private boolean attended = false; // Kulüp yetkilisi okutunca true olacak

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by")
    private UUID checkedInBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_method", length = 10)
    private CheckInMethod checkInMethod;

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
    public Instant getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(Instant registrationTime) { this.registrationTime = registrationTime; }
    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }

    public RegistrationStatus getStatus() { return status; }
    public void setStatus(RegistrationStatus status) { this.status = status; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public Instant getCheckedInAt() { return checkedInAt; }
    public UUID getCheckedInBy() { return checkedInBy; }
    public CheckInMethod getCheckInMethod() { return checkInMethod; }

    public void checkIn(UUID by, CheckInMethod method, Instant at) {
        this.attended = true;
        this.checkedInBy = by;
        this.checkInMethod = method;
        this.checkedInAt = at;
    }

    public void undoCheckIn() {
        this.attended = false;
        this.checkedInBy = null;
        this.checkInMethod = null;
        this.checkedInAt = null;
    }

    public boolean isActive() {
        return status == RegistrationStatus.REGISTERED;
    }
}
