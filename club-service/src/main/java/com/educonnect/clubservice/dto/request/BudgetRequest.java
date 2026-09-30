package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BudgetRequest(Integer academicYear,
                            @NotNull @DecimalMin("0.00") @DecimalMax("10000000.00") @Digits(integer = 10, fraction = 2) BigDecimal plannedIncome,
                            @NotNull @DecimalMin("0.00") @DecimalMax("10000000.00") @Digits(integer = 10, fraction = 2) BigDecimal plannedExpense,
                            @Size(max = 5000) String description,
                            @Size(max = 1000) String note) {
}
