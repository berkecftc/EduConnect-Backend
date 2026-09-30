package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advisor_change_requests")
public class AdvisorChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "proposed_advisor_id", nullable = false)
    private UUID proposedAdvisorId;

    @Column(name = "previous_advisor_id")
    private UUID previousAdvisorId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdvisorChangeRequestStatus status = AdvisorChangeRequestStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected AdvisorChangeRequest() {
    }

    public AdvisorChangeRequest(UUID clubId, UUID proposedAdvisorId, UUID previousAdvisorId, UUID requestedBy, String message) {
        this.clubId = clubId;
        this.proposedAdvisorId = proposedAdvisorId;
        this.previousAdvisorId = previousAdvisorId;
        this.requestedBy = requestedBy;
        this.message = message;
    }

    public boolean isPending() {
        return status == AdvisorChangeRequestStatus.PENDING;
    }

    public void decide(AdvisorChangeRequestStatus decision, String reason, Instant at) {
        this.status = decision;
        this.rejectionReason = reason;
        this.decidedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public UUID getProposedAdvisorId() { return proposedAdvisorId; }
    public UUID getPreviousAdvisorId() { return previousAdvisorId; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getMessage() { return message; }
    public AdvisorChangeRequestStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
