package com.educonnect.assignmentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ExtensionRequest(@NotNull(message = "Yeni son tarih zorunludur") LocalDateTime dueDate,
                               @Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir") String reason) {
}
