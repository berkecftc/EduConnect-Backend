package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_budgets")
public class ClubBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(name = "planned_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal plannedIncome;

    @Column(name = "planned_expense", nullable = false, precision = 12, scale = 2)
    private BigDecimal plannedExpense;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected ClubBudget() {
    }

    public ClubBudget(UUID clubId, UUID requestId, int academicYear, BigDecimal plannedIncome, BigDecimal plannedExpense,
                      String description, UUID preparedBy, Instant createdAt) {
        this.clubId = clubId;
        this.requestId = requestId;
        this.academicYear = academicYear;
        this.plannedIncome = plannedIncome;
        this.plannedExpense = plannedExpense;
        this.description = description;
        this.preparedBy = preparedBy;
        this.createdAt = createdAt;
    }

    public void approve(Instant at) {
        this.approvedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public UUID getRequestId() { return requestId; }
    public int getAcademicYear() { return academicYear; }
    public BigDecimal getPlannedIncome() { return plannedIncome; }
    public BigDecimal getPlannedExpense() { return plannedExpense; }
    public String getDescription() { return description; }
    public UUID getPreparedBy() { return preparedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
