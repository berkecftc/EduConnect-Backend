package com.educonnect.clubservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_creation_requests")
public class ClubCreationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String clubName;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Column(nullable = false)
    private UUID requestingStudentId; // Talebi yapan öğrenci

    private UUID suggestedAdvisorId; // Önerilen danışman hoca

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClubCreationRequestStatus status = ClubCreationRequestStatus.PENDING;

    private Instant requestDate = Instant.now();

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "club_id")
    private UUID clubId;

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
    public ClubCreationRequestStatus getStatus() { return status; }
    public void setStatus(ClubCreationRequestStatus status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public Instant getRequestDate() { return requestDate; }
    public UUID getProcessedBy() { return processedBy; }
    public void setProcessedBy(UUID processedBy) { this.processedBy = processedBy; }
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }
}
