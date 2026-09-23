package com.educonnect.authservices.dto.response;

public record StudentRequestAdminView(
        Long id,
        String firstName,
        String lastName,
        String email,
        String studentNumber,
        String department,
        String studentDocumentUrl
) {}
