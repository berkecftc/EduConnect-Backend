package com.educonnect.authservices.dto.message;

import java.util.UUID;

public class UserDeletedMessage {

    private UUID userId;
    private String userType; // "STUDENT" veya "ACADEMICIAN"
    private String reason;

    public UserDeletedMessage() {
    }

    public UserDeletedMessage(UUID userId, String userType, String reason) {
        this.userId = userId;
        this.userType = userType;
        this.reason = reason;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

