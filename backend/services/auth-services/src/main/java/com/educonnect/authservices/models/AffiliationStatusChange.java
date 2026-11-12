package com.educonnect.authservices.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "affiliation_status_changes")
public class AffiliationStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String affiliation;

    @Column(name = "previous_status", length = 20)
    private String previousStatus;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(length = 1000)
    private String reason;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AffiliationStatusChange() {
    }

    public AffiliationStatusChange(UUID userId, String affiliation, String previousStatus, String status,
                                   LocalDate effectiveDate, String reason, String changedBy) {
        this.userId = userId;
        this.affiliation = affiliation;
        this.previousStatus = previousStatus;
        this.status = status;
        this.effectiveDate = effectiveDate;
        this.reason = reason;
        this.changedBy = changedBy;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getAffiliation() { return affiliation; }
    public String getPreviousStatus() { return previousStatus; }
    public String getStatus() { return status; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public String getReason() { return reason; }
    public String getChangedBy() { return changedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
