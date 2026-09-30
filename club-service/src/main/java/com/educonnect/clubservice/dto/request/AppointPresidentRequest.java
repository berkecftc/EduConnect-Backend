package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AppointPresidentRequest(@NotNull(message = "Atanacak öğrenci zorunludur") UUID studentId) {
}
