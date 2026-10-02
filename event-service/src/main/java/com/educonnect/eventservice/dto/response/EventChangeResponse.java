package com.educonnect.eventservice.dto.response;

import com.educonnect.eventservice.model.EventChange;
import com.educonnect.eventservice.model.EventChangeKind;

import java.time.Instant;
import java.util.UUID;

public record EventChangeResponse(UUID id, EventChangeKind kind, String details, String reason, UUID actorId, Instant createdAt) {

    public static EventChangeResponse of(EventChange change) {
        return new EventChangeResponse(change.getId(), change.getKind(), change.getDetails(), change.getReason(),
                change.getActorId(), change.getCreatedAt());
    }
}
