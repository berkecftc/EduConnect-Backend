package com.educonnect.clubservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_membership_requests")
public class ClubMembershipRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipRequestStatus status = MembershipRequestStatus.PENDING;

    @Column(name = "request_date", nullable = false)
    private Instant requestDate;

    @Column(name = "processed_date")
    private Instant processedDate;

    @Column(name = "processed_by")
    private UUID processedBy; // İşlemi yapan yetkili (Kulüp başkanı)

    @Column(name = "message", columnDefinition = "TEXT")
    private String message; // Öğrencinin başvuru mesajı (opsiyonel)

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; // Red nedeni (opsiyonel)

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", length = 20)
    private MembershipRecommendation recommendation;

    @Column(name = "recommendation_note", columnDefinition = "TEXT")
    private String recommendationNote;

    @Column(name = "recommended_by")
    private UUID recommendedBy;

    @Column(name = "recommended_at")
    private Instant recommendedAt;

    // JPA için no-args constructor
    public ClubMembershipRequest() {}

    // Convenience constructor
    public ClubMembershipRequest(UUID clubId, UUID studentId) {
        this.clubId = clubId;
        this.studentId = studentId;
        this.status = MembershipRequestStatus.PENDING;
        this.requestDate = Instant.now();
    }

    // --- Getter/Setter ---
    public void recommend(MembershipRecommendation recommendation, String note, UUID officerId, Instant at) {
        this.recommendation = recommendation;
        this.recommendationNote = note;
        this.recommendedBy = officerId;
        this.recommendedAt = at;
    }

    public MembershipRecommendation getRecommendation() { return recommendation; }
    public String getRecommendationNote() { return recommendationNote; }
    public UUID getRecommendedBy() { return recommendedBy; }
    public Instant getRecommendedAt() { return recommendedAt; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public MembershipRequestStatus getStatus() { return status; }
    public void setStatus(MembershipRequestStatus status) { this.status = status; }

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
