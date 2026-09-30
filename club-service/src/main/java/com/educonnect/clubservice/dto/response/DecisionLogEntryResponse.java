package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.ClubDecisionLogEntry;
import com.educonnect.clubservice.model.DecisionAction;

import java.time.Instant;
import java.util.UUID;

public record DecisionLogEntryResponse(UUID id,
                                       UUID requestId,
                                       ApprovalType requestType,
                                       DecisionAction action,
                                       UUID actorId,
                                       UUID subjectUserId,
                                       String detail,
                                       Instant createdAt) {

    public static DecisionLogEntryResponse of(ClubDecisionLogEntry entry) {
        return new DecisionLogEntryResponse(entry.getId(), entry.getRequestId(), entry.getRequestType(),
                entry.getAction(), entry.getActorId(), entry.getSubjectUserId(), entry.getDetail(), entry.getCreatedAt());
    }
}
