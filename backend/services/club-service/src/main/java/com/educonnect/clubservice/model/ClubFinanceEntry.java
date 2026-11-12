package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "club_finance_entries")
public class ClubFinanceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "sponsorship_id")
    private UUID sponsorshipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FinanceEntryType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(name = "document_object")
    private String documentObject;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "budget_exceeded", nullable = false)
    private boolean budgetExceeded;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected ClubFinanceEntry() {
    }

    public ClubFinanceEntry(UUID clubId, UUID requestId, FinanceEntryType type, BigDecimal amount, String description,
                            LocalDate occurredOn, int academicYear, UUID preparedBy, Instant createdAt) {
        this.clubId = clubId;
        this.requestId = requestId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.occurredOn = occurredOn;
        this.academicYear = academicYear;
        this.preparedBy = preparedBy;
        this.createdAt = createdAt;
    }

    public void attachDocument(String objectName, String fileName) {
        this.documentObject = objectName;
        this.documentName = fileName;
    }

    public void markBudgetExceeded(boolean exceeded) {
        this.budgetExceeded = exceeded;
    }

    public void linkSponsorship(UUID sponsorshipId) {
        this.sponsorshipId = sponsorshipId;
    }

    public void approve(Instant at) {
        this.approvedAt = at;
    }

    public boolean isApproved() {
        return approvedAt != null;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public UUID getRequestId() { return requestId; }
    public UUID getSponsorshipId() { return sponsorshipId; }
    public FinanceEntryType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public int getAcademicYear() { return academicYear; }
    public String getDocumentObject() { return documentObject; }
    public String getDocumentName() { return documentName; }
    public boolean isBudgetExceeded() { return budgetExceeded; }
    public UUID getPreparedBy() { return preparedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
