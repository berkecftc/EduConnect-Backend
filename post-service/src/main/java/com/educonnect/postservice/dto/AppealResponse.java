package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationAppeal;
import com.educonnect.postservice.model.ModerationTarget;

import java.time.Instant;
import java.util.UUID;

public record AppealResponse(
        UUID id,
        ModerationTarget targetType,
        UUID targetId,
        UUID postId,
        UUID appellantId,
        String statement,
        ModerationAction appealedAction,
        ModerationAppeal.Status status,
        String decisionNote,
        Instant decidedAt,
        Instant createdAt,
        String targetContent
) {

    public static AppealResponse from(ModerationAppeal appeal, String targetContent) {
        return new AppealResponse(appeal.getId(), appeal.getTargetType(), appeal.getTargetId(), appeal.getPostId(),
                appeal.getAppellantId(), appeal.getStatement(), appeal.getAppealedAction(), appeal.getStatus(),
                appeal.getDecisionNote(), appeal.getDecidedAt(), appeal.getCreatedAt(), targetContent);
    }
}
