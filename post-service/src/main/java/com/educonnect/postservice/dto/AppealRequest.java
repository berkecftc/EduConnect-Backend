package com.educonnect.postservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppealRequest(
        @NotBlank(message = "İtiraz gerekçesi zorunludur")
        @Size(max = 2000, message = "İtiraz gerekçesi en fazla 2000 karakter olabilir")
        String statement
) {}
