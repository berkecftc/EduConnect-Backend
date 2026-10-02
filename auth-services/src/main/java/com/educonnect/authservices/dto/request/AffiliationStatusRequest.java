package com.educonnect.authservices.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AffiliationStatusRequest(
        @NotBlank(message = "Durum zorunludur") String status,
        LocalDate effectiveDate,
        @Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir") String reason) {
}
