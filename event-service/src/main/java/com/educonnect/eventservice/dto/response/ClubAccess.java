package com.educonnect.eventservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubAccess(UUID clubId,
                         UUID userId,
                         String position,
                         boolean member,
                         boolean actingPresident,
                         boolean advisor,
                         Set<String> permissions) {

    public boolean has(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
