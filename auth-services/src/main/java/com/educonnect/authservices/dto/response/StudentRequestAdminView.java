package com.educonnect.authservices.dto.response;

import java.util.UUID;

public record StudentRequestAdminView(
        Long id,
        String firstName,
        String lastName,
        String email,
        String studentNumber,
        String department,
        String studentDocumentUrl,
        boolean emailVerified,
        boolean additionalAffiliation,
        UUID programId
) {}
