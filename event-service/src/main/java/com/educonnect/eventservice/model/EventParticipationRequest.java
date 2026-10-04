package com.educonnect.eventservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Etkinlik katılım isteği modeli.
 * Öğrenci üye olduğu kulübün etkinliğine katılma isteği gönderir,
 * kulüp başkanı onayladıktan sonra etkinliğe kayıt gerçekleşir.
 */
@Entity
@Table(name = "event_participation_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "student_id"}))
public class EventParticipationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ParticipationRequestStatus status = ParticipationRequestStatus.PENDING;

    @Column(name = "request_date", nullable = false)
    private Instant requestDate;

    @Column(name = "processed_date")
    private Instant processedDate;

    @Column(name = "processed_by")
    private UUID processedBy; // İşlemi yapan kulüp yetkilisi

    @Column(name = "message", columnDefinition = "TEXT")
    private String message; // Öğrencinin opsiyonel mesajı

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; // Red nedeni (opsiyonel)

    // JPA için no-args constructor
    public EventParticipationRequest() {}

    // Convenience constructor
    public EventParticipationRequest(UUID eventId, UUID studentId) {
        this.eventId = eventId;
        this.studentId = studentId;
        this.status = ParticipationRequestStatus.PENDING;
        this.requestDate = Instant.now();
    }

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public ParticipationRequestStatus getStatus() { return status; }
    public void setStatus(ParticipationRequestStatus status) { this.status = status; }

    public Instant getRequestDate() { return requestDate; }
    public void setRequestDate(Instant requestDate) { this.requestDate = requestDate; }

    public Instant getProcessedDate() { return processedDate; }
    public void setProcessedDate(Instant processedDate) { this.processedDate = processedDate; }

    public UUID getProcessedBy() { return processedBy; }
    public void setProcessedBy(UUID processedBy) { this.processedBy = processedBy; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
