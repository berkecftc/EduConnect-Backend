package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.FinanceEntryType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceEntryForm(@NotNull FinanceEntryType type,
                               @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                               @NotBlank @Size(max = 500) String description,
                               @NotNull @PastOrPresent @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredOn,
                               @Size(max = 1000) String note) {
}
