package com.educonnect.gamificationservice.dto.event;

import com.educonnect.gamificationservice.model.ActionType;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public class GamificationEvent implements Serializable {
    private UUID userId;
    private ActionType actionType;
    private String referenceId;
    private OffsetDateTime occurredAt;

    public GamificationEvent() {
    }

    public GamificationEvent(UUID userId, ActionType actionType, String referenceId, OffsetDateTime occurredAt) {
        this.userId = userId;
        this.actionType = actionType;
        this.referenceId = referenceId;
        this.occurredAt = occurredAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}

