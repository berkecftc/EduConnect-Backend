package com.educonnect.postservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_appeals")
public class ModerationAppeal {

    public enum Status {
        OPEN,
        ACCEPTED,
        REJECTED
    }

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ModerationTarget targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "appellant_id")
    private UUID appellantId;

    @Column(nullable = false, length = 2000)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(name = "appealed_action", nullable = false, length = 20)
    private ModerationAction appealedAction;

    @Column(name = "original_decider_id")
    private UUID originalDeciderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModerationAppeal() {
    }

    public ModerationAppeal(ModerationTarget targetType, UUID targetId, UUID postId, UUID appellantId, String statement,
                            ModerationAction appealedAction, UUID originalDeciderId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.targetType = targetType;
        this.targetId = targetId;
        this.postId = postId;
        this.appellantId = appellantId;
        this.statement = statement;
        this.appealedAction = appealedAction;
        this.originalDeciderId = originalDeciderId;
        this.createdAt = createdAt;
    }

    public void decide(Status outcome, UUID decider, String note, Instant at) {
        this.status = outcome;
        this.decidedBy = decider;
        this.decisionNote = note;
        this.decidedAt = at;
    }

    public UUID getId() { return id; }
    public ModerationTarget getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public UUID getPostId() { return postId; }
    public UUID getAppellantId() { return appellantId; }
    public String getStatement() { return statement; }
    public ModerationAction getAppealedAction() { return appealedAction; }
    public UUID getOriginalDeciderId() { return originalDeciderId; }
    public Status getStatus() { return status; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
}
