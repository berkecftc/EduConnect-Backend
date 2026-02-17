package com.educonnect.clubservice.model;

/**
 * Görev değişikliği taleplerinin durumu
 */
public enum RoleChangeRequestStatus {
    PENDING,    // Danışman onayı bekliyor
    APPROVED,   // Danışman onayladı
    REJECTED    // Danışman reddetti
}

