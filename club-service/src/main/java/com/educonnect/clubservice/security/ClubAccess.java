package com.educonnect.clubservice.security;

import com.educonnect.clubservice.model.ClubPosition;

import java.util.Set;
import java.util.UUID;

public record ClubAccess(UUID clubId,
                         UUID userId,
                         ClubPosition position,
                         boolean actingPresident,
                         boolean advisor,
                         Set<ClubPermission> permissions) {

    public boolean member() {
        return position != null;
    }

    public boolean has(ClubPermission permission) {
        return permissions.contains(permission);
    }
}
