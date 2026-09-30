package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AdvisorChangeProposal(
        @NotNull(message = "Önerilen danışman zorunludur") UUID proposedAdvisorId,
        @Size(max = 1000, message = "Mesaj en fazla 1000 karakter olabilir") String message) {
}
