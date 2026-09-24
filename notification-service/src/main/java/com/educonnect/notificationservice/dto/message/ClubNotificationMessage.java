package com.educonnect.notificationservice.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubNotificationMessage(
        UUID targetUserId,
        UUID clubId,
        String clubName,
        String subject,
        String message
) {
}
