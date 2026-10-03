package com.educonnect.postservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_records")
public class ModerationRecord {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ModerationTarget targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ModerationActor actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModerationRecord() {
    }

    public ModerationRecord(ModerationTarget targetType, UUID targetId, UUID postId, ModerationAction action,
                            ModerationActor actorType, UUID actorId, String reason, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.targetType = targetType;
        this.targetId = targetId;
        this.postId = postId;
        this.action = action;
        this.actorType = actorType;
        this.actorId = actorId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public ModerationTarget getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public UUID getPostId() { return postId; }
    public ModerationAction getAction() { return action; }
    public ModerationActor getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
