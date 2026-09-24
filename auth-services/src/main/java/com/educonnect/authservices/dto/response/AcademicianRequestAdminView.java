package com.educonnect.authservices.dto.response;

import java.util.UUID;

public record AcademicianRequestAdminView(
        Long id,
        UUID userId,
        String firstName,
        String lastName,
        String title,
        String department,
        String officeNumber,
        String idCardImageUrl,
        boolean emailVerified
) {}
