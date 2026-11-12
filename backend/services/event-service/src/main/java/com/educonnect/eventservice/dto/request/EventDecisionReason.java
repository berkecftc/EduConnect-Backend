package com.educonnect.eventservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventDecisionReason(
        @NotBlank(message = "Gerekçe zorunludur")
        @Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir") String reason) {
}
