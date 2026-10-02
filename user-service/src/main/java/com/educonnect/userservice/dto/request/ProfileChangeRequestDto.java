package com.educonnect.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProfileChangeRequestDto(
        @Size(max = 255, message = "Ad en fazla 255 karakter olabilir") String firstName,
        @Size(max = 255, message = "Soyad en fazla 255 karakter olabilir") String lastName,
        @Size(max = 255, message = "Unvan en fazla 255 karakter olabilir") String title,
        UUID programId,
        UUID departmentId,
        @NotBlank(message = "Gerekçe zorunludur") @Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir") String reason) {
}
