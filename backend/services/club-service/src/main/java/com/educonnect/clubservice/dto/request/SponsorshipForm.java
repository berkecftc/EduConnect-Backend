package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SponsorshipForm(@NotBlank @Size(max = 200) String sponsorName,
                              @NotBlank @Size(max = 5000) String description,
                              @DecimalMin("0.00") @DecimalMax("10000000.00") @Digits(integer = 10, fraction = 2) BigDecimal cashAmount,
                              @Size(max = 1000) String inKind,
                              @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsOn,
                              @Size(max = 1000) String note) {
}
