package com.educonnect.authservices.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StudentAffiliationRequest(
        @Size(max = 32, message = "Öğrenci numarası en fazla 32 karakter olabilir") String studentNumber,
        UUID programId,
        @Min(value = 1950, message = "Giriş yılı geçersiz") @Max(value = 2200, message = "Giriş yılı geçersiz") Integer entryYear,
        @Size(max = 255, message = "Bölüm en fazla 255 karakter olabilir") String department) {
}
