package com.educonnect.postservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModeratorDecisionRequest(
        @NotNull(message = "Karar seçilmelidir")
        Action action,

        @Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir")
        String reason
) {

    public enum Action {
        APPROVE,
        REJECT
    }
}
