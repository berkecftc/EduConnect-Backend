package com.educonnect.clubservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceSummaryResponse(int academicYear,
                                     String academicYearLabel,
                                     BudgetResponse budget,
                                     BigDecimal income,
                                     BigDecimal expense,
                                     BigDecimal balance,
                                     BigDecimal overallBalance,
                                     BigDecimal advisorThreshold,
                                     LocalDate academicYearEnd) {
}
