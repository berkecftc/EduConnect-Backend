package com.educonnect.authservices.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank(message = "Token gereklidir") String token) {
}
