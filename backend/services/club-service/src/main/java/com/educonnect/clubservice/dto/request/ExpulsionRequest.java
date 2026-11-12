package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ExpulsionRequest(
        @NotNull(message = "Öğrenci zorunludur") UUID studentId,
        @NotBlank(message = "Gerekçe zorunludur")
        @Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir") String reason) {
}
