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
@Table(name = "content_reports")
public class ContentReport {

    public enum Status {
        OPEN,
        UPHELD,
        DISMISSED
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

    @Column(name = "reporter_id")
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 1000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContentReport() {
    }

    public ContentReport(ModerationTarget targetType, UUID targetId, UUID postId, UUID reporterId,
                         ReportReason reason, String details, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.targetType = targetType;
        this.targetId = targetId;
        this.postId = postId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.details = details;
        this.createdAt = createdAt;
    }

    public void resolve(Status outcome, UUID moderator, String note, Instant at) {
        this.status = outcome;
        this.resolvedBy = moderator;
        this.resolutionNote = note;
        this.resolvedAt = at;
    }

    public UUID getId() { return id; }
    public ModerationTarget getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public UUID getPostId() { return postId; }
    public UUID getReporterId() { return reporterId; }
    public ReportReason getReason() { return reason; }
    public String getDetails() { return details; }
    public Status getStatus() { return status; }
    public UUID getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getCreatedAt() { return createdAt; }
}
