package com.educonnect.eventservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventChangeRequest(
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        @Size(max = 255, message = "Konum en fazla 255 karakter olabilir") String location,
        @NotBlank(message = "Gerekçe zorunludur") @Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir") String reason) {
}
