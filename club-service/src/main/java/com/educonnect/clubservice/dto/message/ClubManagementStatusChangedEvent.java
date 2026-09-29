package com.educonnect.clubservice.dto.message;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ClubManagementStatusChangedEvent(UUID eventId,
                                               String eventType,
                                               int schemaVersion,
                                               Instant occurredAt,
                                               UUID userId,
                                               boolean managesClub) implements Serializable {

    public static final String EVENT_TYPE = "ClubManagementStatusChanged";
    public static final int SCHEMA_VERSION = 1;

    public static ClubManagementStatusChangedEvent of(UUID userId, boolean managesClub, Instant occurredAt) {
        return new ClubManagementStatusChangedEvent(UUID.randomUUID(), EVENT_TYPE, SCHEMA_VERSION, occurredAt, userId, managesClub);
    }
}
