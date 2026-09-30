package com.educonnect.clubservice.model;

public enum ElectionStatus {
    CANDIDACY,
    VOTING,
    AWAITING_APPROVAL,
    COMPLETED,
    CANCELLED;

    public boolean isOpen() {
        return this == CANDIDACY || this == VOTING || this == AWAITING_APPROVAL;
    }
}
