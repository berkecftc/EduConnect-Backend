package com.educonnect.authservices.models;

public enum StaffPermission {
    STUDENT_VERIFIER,
    STAFF_VERIFIER,
    MODERATOR,
    CAMPUS_PUBLISHER,
    ACCOUNT_MANAGER;

    public static final String AUTHORITY_PREFIX = "PERM_";

    public boolean facultyScoped() {
        return this == STUDENT_VERIFIER;
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
