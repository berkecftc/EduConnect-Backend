package com.educonnect.authservices.models;

/**
 * Kullanıcı rollerini tanımlayan Enum.
 * Spring Security'nin beklemesi için 'ROLE_' ön eki eklenir.
 */
public enum Role {
    ROLE_STUDENT,     // Öğrenci
    ROLE_PENDING_STUDENT, // Öğrenci (onay bekleyen)
    ROLE_ACADEMICIAN,
    ROLE_PENDING_ACADEMICIAN,// Akademisyen (onay bekleyen)
    ROLE_CLUB_OFFICIAL, // Kulüp Yetkilisi
    ROLE_PENDING_CLUB_OFFICIAL, // Kulüp Yetkilisi (onay bekleyen)
    ROLE_ADMIN        // Admin
}