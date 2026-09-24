package com.educonnect.clubservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "club_creation_requests")
public class ClubCreationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String clubName;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Column(nullable = false)
    private UUID requestingStudentId; // Talebi yapan öğrenci

    private UUID suggestedAdvisorId; // Önerilen danışman hoca

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    private LocalDateTime requestDate = LocalDateTime.now();

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private UUID processedBy;

    // --- Getter ve Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public UUID getRequestingStudentId() { return requestingStudentId; }
    public void setRequestingStudentId(UUID requestingStudentId) { this.requestingStudentId = requestingStudentId; }
    public UUID getSuggestedAdvisorId() { return suggestedAdvisorId; }
    public void setSuggestedAdvisorId(UUID suggestedAdvisorId) { this.suggestedAdvisorId = suggestedAdvisorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public UUID getProcessedBy() { return processedBy; }
    public void setProcessedBy(UUID processedBy) { this.processedBy = processedBy; }
}