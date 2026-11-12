package com.educonnect.notificationservice.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailVerificationMessage(String email, String firstName, String verificationLink, long validHours, String purpose) {

    public static final String EMAIL_CHANGE = "EMAIL_CHANGE";
    public static final String EMAIL_CHANGED = "EMAIL_CHANGED";
}
