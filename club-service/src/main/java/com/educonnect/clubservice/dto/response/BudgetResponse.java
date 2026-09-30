package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ClubBudget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(UUID id,
                             int academicYear,
                             String academicYearLabel,
                             BigDecimal plannedIncome,
                             BigDecimal plannedExpense,
                             String description,
                             UUID preparedBy,
                             Instant createdAt,
                             Instant approvedAt,
                             ApprovalStatus status) {

    public static BudgetResponse of(ClubBudget budget, ApprovalStatus status) {
        if (budget == null) {
            return null;
        }
        return new BudgetResponse(budget.getId(), budget.getAcademicYear(), AcademicYears.label(budget.getAcademicYear()),
                budget.getPlannedIncome(), budget.getPlannedExpense(), budget.getDescription(), budget.getPreparedBy(),
                budget.getCreatedAt(), budget.getApprovedAt(), status);
    }
}
