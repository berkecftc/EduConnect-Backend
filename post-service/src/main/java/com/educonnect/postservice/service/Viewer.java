package com.educonnect.postservice.service;

import com.educonnect.postservice.exception.UnauthorizedPostAccessException;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record Viewer(UUID id, Set<String> roles) {

    static final String STUDENT = "ROLE_STUDENT";
    static final String CLUB_OFFICIAL = "ROLE_CLUB_OFFICIAL";
    static final String ACADEMICIAN = "ROLE_ACADEMICIAN";
    static final String STAFF = "ROLE_STAFF";
    static final String ADMIN = "ROLE_ADMIN";
    static final String CAMPUS_PUBLISHER = "PERM_CAMPUS_PUBLISHER";

    private static final Set<String> COMMUNITY_ROLES = Set.of(STUDENT, CLUB_OFFICIAL, ACADEMICIAN, STAFF, ADMIN);

    public static Viewer of(String userId, String rolesHeader) {
        Set<String> roles = rolesHeader == null ? Set.of() : Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new Viewer(UUID.fromString(userId), roles);
    }

    public static Viewer reader(String userId, String rolesHeader) {
        Viewer viewer = of(userId, rolesHeader);
        viewer.requireReader();
        return viewer;
    }

    public void requireReader() {
        if (roles.stream().noneMatch(COMMUNITY_ROLES::contains)) {
            throw new UnauthorizedPostAccessException("Topluluk sayfasına erişim yetkiniz bulunmamaktadır.");
        }
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    public boolean student() {
        return has(STUDENT) || has(CLUB_OFFICIAL);
    }

    public boolean academician() {
        return has(ACADEMICIAN);
    }

    public boolean admin() {
        return has(ADMIN);
    }

    public boolean seesAllScopes() {
        return has(ADMIN) || has(STAFF);
    }

    public boolean campusPublisher() {
        return has(ADMIN) || has(CAMPUS_PUBLISHER);
    }
}
