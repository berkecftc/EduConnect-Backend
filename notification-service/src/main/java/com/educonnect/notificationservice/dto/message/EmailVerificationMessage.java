package com.educonnect.notificationservice.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailVerificationMessage(String email, String firstName, String verificationLink, long validHours) {
}
