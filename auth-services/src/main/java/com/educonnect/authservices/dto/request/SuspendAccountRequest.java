package com.educonnect.authservices.dto.request;

import jakarta.validation.constraints.Size;

public record SuspendAccountRequest(
        @Size(max = 500, message = "Askıya alma gerekçesi en fazla 500 karakter olabilir")
        String reason) {
}
