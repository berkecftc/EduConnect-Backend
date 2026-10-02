package com.educonnect.authservices.models;

import java.util.Collection;

public enum AccountType {
    ADMIN,
    STAFF,
    ACADEMICIAN,
    STUDENT,
    UNKNOWN;

    public static AccountType of(Collection<Role> roles) {
        if (roles == null) {
            return UNKNOWN;
        }
        if (roles.contains(Role.ROLE_ADMIN)) {
            return ADMIN;
        }
        if (roles.contains(Role.ROLE_STAFF)) {
            return STAFF;
        }
        if (roles.contains(Role.ROLE_ACADEMICIAN)) {
            return ACADEMICIAN;
        }
        if (roles.contains(Role.ROLE_STUDENT)) {
            return STUDENT;
        }
        return UNKNOWN;
    }
}
