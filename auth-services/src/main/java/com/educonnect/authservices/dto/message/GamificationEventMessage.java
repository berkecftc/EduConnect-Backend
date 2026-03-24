package com.educonnect.authservices.dto.message;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public class GamificationEventMessage implements Serializable {

    private UUID userId;
    private GamificationActionType actionType;
    private String referenceId;
    private OffsetDateTime occurredAt;

    public GamificationEventMessage() {
    }

    public GamificationEventMessage(UUID userId, GamificationActionType actionType, String referenceId, OffsetDateTime occurredAt) {
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

    public GamificationActionType getActionType() {
        return actionType;
    }

    public void setActionType(GamificationActionType actionType) {
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

