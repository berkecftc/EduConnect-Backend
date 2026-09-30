package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DefenceRequest(
        @NotBlank(message = "Savunma zorunludur")
        @Size(max = 2000, message = "Savunma en fazla 2000 karakter olabilir") String note) {
}
