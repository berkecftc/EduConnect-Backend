package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.ClubDecisionLogEntry;
import com.educonnect.clubservice.model.DecisionAction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DecisionLogEntryResponse(UUID id,
                                       UUID requestId,
                                       ApprovalType requestType,
                                       DecisionAction action,
                                       UUID actorId,
                                       String actorName,
                                       UUID subjectUserId,
                                       String subjectUserName,
                                       String detail,
                                       Instant createdAt) {

    public static DecisionLogEntryResponse of(ClubDecisionLogEntry entry, Map<UUID, String> names) {
        return new DecisionLogEntryResponse(entry.getId(), entry.getRequestId(), entry.getRequestType(),
                entry.getAction(), entry.getActorId(), names.get(entry.getActorId()),
                entry.getSubjectUserId(), names.get(entry.getSubjectUserId()), entry.getDetail(), entry.getCreatedAt());
    }
}
