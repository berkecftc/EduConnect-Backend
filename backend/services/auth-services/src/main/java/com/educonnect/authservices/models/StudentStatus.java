package com.educonnect.authservices.models;

import java.util.Set;

public enum StudentStatus {
    ACTIVE,
    ON_LEAVE,
    GRADUATED,
    WITHDRAWN,
    EXPELLED,
    TRANSFERRED_OUT;

    private static final Set<StudentStatus> ENDED = Set.of(GRADUATED, WITHDRAWN, EXPELLED, TRANSFERRED_OUT);

    public boolean ended() {
        return ENDED.contains(this);
    }
}
