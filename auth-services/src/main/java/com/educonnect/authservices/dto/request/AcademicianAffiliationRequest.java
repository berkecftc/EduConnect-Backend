package com.educonnect.authservices.dto.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AcademicianAffiliationRequest(
        @Size(max = 255, message = "Unvan en fazla 255 karakter olabilir") String title,
        UUID departmentId,
        @Size(max = 255, message = "Bölüm en fazla 255 karakter olabilir") String department,
        @Size(max = 255, message = "Ofis numarası en fazla 255 karakter olabilir") String officeNumber) {
}
