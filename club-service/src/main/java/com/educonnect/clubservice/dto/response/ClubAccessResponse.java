package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.security.ClubAccess;
import com.educonnect.clubservice.security.ClubPermission;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ClubAccessResponse(UUID clubId,
                                 UUID userId,
                                 String position,
                                 boolean member,
                                 boolean actingPresident,
                                 boolean advisor,
                                 Set<String> permissions) {

    public static ClubAccessResponse from(ClubAccess access) {
        return new ClubAccessResponse(
                access.clubId(),
                access.userId(),
                access.position() != null ? access.position().name() : null,
                access.member(),
                access.actingPresident(),
                access.advisor(),
                access.permissions().stream().map(ClubPermission::name).collect(Collectors.toUnmodifiableSet()));
    }
}
