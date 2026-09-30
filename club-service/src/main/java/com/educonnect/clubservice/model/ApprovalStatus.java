package com.educonnect.clubservice.model;

import java.util.EnumSet;
import java.util.Set;

public enum ApprovalStatus {
    PENDING_PRESIDENT,
    PENDING_ADVISOR,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    public static final Set<ApprovalStatus> PENDING = EnumSet.of(PENDING_PRESIDENT, PENDING_ADVISOR);

    public boolean isPending() {
        return PENDING.contains(this);
    }

    public RoleChangeRequestStatus toLegacy() {
        return switch (this) {
            case PENDING_PRESIDENT, PENDING_ADVISOR -> RoleChangeRequestStatus.PENDING;
            case APPROVED -> RoleChangeRequestStatus.APPROVED;
            case REJECTED, WITHDRAWN -> RoleChangeRequestStatus.REJECTED;
        };
    }
}
