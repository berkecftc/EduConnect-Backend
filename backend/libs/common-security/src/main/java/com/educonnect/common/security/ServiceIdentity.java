package com.educonnect.common.security;

import java.util.regex.Pattern;

public final class ServiceIdentity {

    public static final Pattern INTERNAL_PATH = Pattern.compile("^/api/[^/]+/internal(/.*)?$");

    public static final String ROLE = "SERVICE";
    public static final String AUTHORITY = "ROLE_" + ROLE;
    public static final String TOKEN_USE_CLAIM = "token_use";
    public static final String TOKEN_USE_SERVICE = "service";
    public static final String DEFAULT_INTERNAL_AUDIENCE = "educonnect-internal";

    private ServiceIdentity() {
    }
}
