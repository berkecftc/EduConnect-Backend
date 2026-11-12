package com.educonnect.postservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppealDecisionRequest(
        @NotNull(message = "Karar seçilmelidir")
        Outcome outcome,

        @NotBlank(message = "Karar gerekçesi zorunludur")
        @Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir")
        String reason
) {

    public enum Outcome {
        ACCEPT,
        REJECT
    }
}
