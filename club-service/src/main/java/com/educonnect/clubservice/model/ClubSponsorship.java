package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "club_sponsorships")
public class ClubSponsorship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "sponsor_name", nullable = false, length = 200)
    private String sponsorName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "cash_amount", precision = 12, scale = 2)
    private BigDecimal cashAmount;

    @Column(name = "in_kind", length = 1000)
    private String inKind;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "document_object")
    private String documentObject;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected ClubSponsorship() {
    }

    public ClubSponsorship(UUID clubId, UUID requestId, String sponsorName, String description, BigDecimal cashAmount,
                           String inKind, LocalDate startsOn, LocalDate endsOn, UUID preparedBy, Instant createdAt) {
        this.clubId = clubId;
        this.requestId = requestId;
        this.sponsorName = sponsorName;
        this.description = description;
        this.cashAmount = cashAmount;
        this.inKind = inKind;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.preparedBy = preparedBy;
        this.createdAt = createdAt;
    }

    public void attachDocument(String objectName, String fileName) {
        this.documentObject = objectName;
        this.documentName = fileName;
    }

    public void approve(Instant at) {
        this.approvedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public UUID getRequestId() { return requestId; }
    public String getSponsorName() { return sponsorName; }
    public String getDescription() { return description; }
    public BigDecimal getCashAmount() { return cashAmount; }
    public String getInKind() { return inKind; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public String getDocumentObject() { return documentObject; }
    public String getDocumentName() { return documentName; }
    public UUID getPreparedBy() { return preparedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
