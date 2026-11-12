package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
@Table(name = "club_reports")
public class ClubReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportType type;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status = ReportStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "finance_note", columnDefinition = "TEXT")
    private String financeNote;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Embedded
    private ReportSnapshot snapshot;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected ClubReport() {
    }

    public ClubReport(UUID clubId, ReportType type, int academicYear, UUID createdBy, Instant createdAt) {
        this.clubId = clubId;
        this.type = type;
        this.academicYear = academicYear;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public boolean isDraft() {
        return status == ReportStatus.DRAFT;
    }

    public void writeBody(String body, String recommendations, UUID by, Instant at) {
        this.body = body;
        this.recommendations = recommendations;
        touch(by, at);
    }

    public void writeFinanceNote(String financeNote, UUID by, Instant at) {
        this.financeNote = financeNote;
        touch(by, at);
    }

    public void submit(UUID requestId, ReportSnapshot snapshot, UUID by, Instant at) {
        this.requestId = requestId;
        this.snapshot = snapshot;
        this.status = ReportStatus.SUBMITTED;
        this.submittedAt = at;
        touch(by, at);
    }

    public void approve(Instant at) {
        this.status = ReportStatus.APPROVED;
        this.approvedAt = at;
    }

    public void reopen() {
        this.status = ReportStatus.DRAFT;
        this.submittedAt = null;
    }

    private void touch(UUID by, Instant at) {
        this.updatedBy = by;
        this.updatedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public ReportType getType() { return type; }
    public int getAcademicYear() { return academicYear; }
    public ReportStatus getStatus() { return status; }
    public String getBody() { return body; }
    public String getFinanceNote() { return financeNote; }
    public String getRecommendations() { return recommendations; }
    public ReportSnapshot getSnapshot() { return snapshot; }
    public UUID getRequestId() { return requestId; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
