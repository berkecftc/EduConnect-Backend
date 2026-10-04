package com.educonnect.eventservice.dto.response;

import com.educonnect.eventservice.model.EventStaff;

import java.time.Instant;
import java.util.UUID;

public record EventStaffResponse(UUID eventId, UUID userId, String firstName, String lastName, EventStaff.Status status,
                                 UUID proposedBy, UUID approvedBy, Instant createdAt, Instant decidedAt) {

    public static EventStaffResponse of(EventStaff staff, UserSummary user) {
        return new EventStaffResponse(staff.getEventId(), staff.getUserId(), user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null, staff.getStatus(), staff.getProposedBy(), staff.getApprovedBy(),
                staff.getCreatedAt(), staff.getDecidedAt());
    }
}
