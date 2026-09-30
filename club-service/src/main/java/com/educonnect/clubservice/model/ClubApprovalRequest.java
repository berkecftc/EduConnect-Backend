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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_approval_requests")
public class ClubApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApprovalType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApprovalStatus status;

    @Column(name = "prepared_by")
    private UUID preparedBy;

    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_position", length = 40)
    private ClubPosition currentPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_position", length = 40)
    private ClubPosition requestedPosition;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "president_decided_by")
    private UUID presidentDecidedBy;

    @Column(name = "president_decided_at")
    private Instant presidentDecidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ClubApprovalRequest() {
    }

    public ClubApprovalRequest(UUID clubId, ApprovalType type, UUID preparedBy, UUID subjectUserId,
                               ClubPosition currentPosition, ClubPosition requestedPosition, String note,
                               Instant createdAt) {
        this.clubId = clubId;
        this.type = type;
        this.preparedBy = preparedBy;
        this.subjectUserId = subjectUserId;
        this.currentPosition = currentPosition;
        this.requestedPosition = requestedPosition;
        this.note = note;
        this.createdAt = createdAt;
        this.status = ApprovalStatus.PENDING_ADVISOR;
    }

    public boolean isPending() {
        return status.isPending();
    }

    public void awaitPresident() {
        this.status = ApprovalStatus.PENDING_PRESIDENT;
    }

    public void approveAsPresident(UUID presidentId, Instant at) {
        this.presidentDecidedBy = presidentId;
        this.presidentDecidedAt = at;
        this.status = ApprovalStatus.PENDING_ADVISOR;
    }

    public void conclude(ApprovalStatus outcome, UUID deciderId, String reason, Instant at) {
        this.status = outcome;
        this.decidedBy = deciderId;
        this.rejectionReason = reason;
        this.decidedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public ApprovalType getType() { return type; }
    public ApprovalStatus getStatus() { return status; }
    public UUID getPreparedBy() { return preparedBy; }
    public UUID getSubjectUserId() { return subjectUserId; }
    public ClubPosition getCurrentPosition() { return currentPosition; }
    public ClubPosition getRequestedPosition() { return requestedPosition; }
    public String getNote() { return note; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getPresidentDecidedBy() { return presidentDecidedBy; }
    public Instant getPresidentDecidedAt() { return presidentDecidedAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
}
