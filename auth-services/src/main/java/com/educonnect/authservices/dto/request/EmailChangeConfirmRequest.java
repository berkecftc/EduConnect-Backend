package com.educonnect.authservices.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EmailChangeConfirmRequest(@NotBlank(message = "Token gereklidir") String token) {
}
