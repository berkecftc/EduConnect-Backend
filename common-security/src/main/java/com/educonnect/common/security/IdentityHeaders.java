package com.educonnect.common.security;

import java.util.Set;

public final class IdentityHeaders {

    public static final String USER_ID = "X-Authenticated-User-Id";
    public static final String USER_EMAIL = "X-Authenticated-User-Email";
    public static final String USER_ROLES = "X-Authenticated-User-Roles";

    public static final Set<String> ALL = Set.of(USER_ID, USER_EMAIL, USER_ROLES);

    private IdentityHeaders() {
    }

    public static boolean isIdentityHeader(String name) {
        return name != null && ALL.stream().anyMatch(h -> h.equalsIgnoreCase(name));
    }
}
