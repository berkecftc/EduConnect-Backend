package com.educonnect.authservices.dto.message;

public record EmailVerificationMessage(String email, String firstName, String verificationLink, long validHours, String purpose) {

    public static final String EMAIL_CHANGED = "EMAIL_CHANGED";

    public EmailVerificationMessage(String email, String firstName, String verificationLink, long validHours) {
        this(email, firstName, verificationLink, validHours, null);
    }
}
