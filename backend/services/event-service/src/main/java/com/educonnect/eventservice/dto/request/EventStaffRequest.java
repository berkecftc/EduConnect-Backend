package com.educonnect.eventservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EventStaffRequest(@NotNull(message = "Görevli seçilmelidir") UUID userId) {
}
