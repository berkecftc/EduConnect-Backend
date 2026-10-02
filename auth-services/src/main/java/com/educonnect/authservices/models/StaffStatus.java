package com.educonnect.authservices.models;

public enum StaffStatus {
    ACTIVE,
    ON_LEAVE,
    RETIRED,
    RESIGNED,
    TERMINATED;

    public boolean ended() {
        return this == RETIRED || this == RESIGNED || this == TERMINATED;
    }
}
