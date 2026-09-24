package com.educonnect.authservices.dto.message;

public record EmailVerificationMessage(String email, String firstName, String verificationLink, long validHours) {
}
