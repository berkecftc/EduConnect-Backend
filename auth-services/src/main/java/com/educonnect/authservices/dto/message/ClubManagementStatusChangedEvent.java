package com.educonnect.authservices.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubManagementStatusChangedEvent(UUID eventId,
                                               String eventType,
                                               int schemaVersion,
                                               Instant occurredAt,
                                               UUID userId,
                                               boolean managesClub) {

    public static final String EVENT_TYPE = "ClubManagementStatusChanged";
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
}
