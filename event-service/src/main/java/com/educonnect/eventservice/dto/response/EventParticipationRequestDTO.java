package com.educonnect.eventservice.dto.response;

import com.educonnect.eventservice.model.ParticipationRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Etkinlik katılım isteği yanıt DTO'su
 */
public class EventParticipationRequestDTO {

    private UUID id;
    private UUID eventId;
    private String eventTitle;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private ParticipationRequestStatus status;
    private LocalDateTime requestDate;
    private LocalDateTime processedDate;
    private String message;
    private String rejectionReason;

    public EventParticipationRequestDTO() {}

    // --- Getter/Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }

    public ParticipationRequestStatus getStatus() { return status; }
    public void setStatus(ParticipationRequestStatus status) { this.status = status; }

    public LocalDateTime getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDateTime requestDate) { this.requestDate = requestDate; }

    public LocalDateTime getProcessedDate() { return processedDate; }
    public void setProcessedDate(LocalDateTime processedDate) { this.processedDate = processedDate; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}

