package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationActor;
import com.educonnect.postservice.model.ModerationRecord;
import com.educonnect.postservice.model.ModerationTarget;

import java.time.Instant;
import java.util.UUID;

public record ModerationRecordResponse(
        UUID id,
        ModerationTarget targetType,
        UUID targetId,
        UUID postId,
        ModerationAction action,
        ModerationActor actorType,
        UUID actorId,
        String reason,
        Instant createdAt
) {

    public static ModerationRecordResponse from(ModerationRecord record) {
        return new ModerationRecordResponse(record.getId(), record.getTargetType(), record.getTargetId(),
                record.getPostId(), record.getAction(), record.getActorType(), record.getActorId(),
                record.getReason(), record.getCreatedAt());
    }
}
