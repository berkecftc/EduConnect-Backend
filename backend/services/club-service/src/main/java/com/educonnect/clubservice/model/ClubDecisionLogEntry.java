package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_decision_log")
public class ClubDecisionLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false, updatable = false)
    private UUID clubId;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", length = 30, updatable = false)
    private ApprovalType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private DecisionAction action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClubDecisionLogEntry() {
    }

    public ClubDecisionLogEntry(UUID clubId, UUID requestId, ApprovalType requestType, DecisionAction action,
                                UUID actorId, UUID subjectUserId, String detail, Instant createdAt) {
        this.clubId = clubId;
        this.requestId = requestId;
        this.requestType = requestType;
        this.action = action;
        this.actorId = actorId;
        this.subjectUserId = subjectUserId;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public UUID getRequestId() { return requestId; }
    public ApprovalType getRequestType() { return requestType; }
    public DecisionAction getAction() { return action; }
    public UUID getActorId() { return actorId; }
    public UUID getSubjectUserId() { return subjectUserId; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
