package com.educonnect.notificationservice.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubMembershipNotificationMessage(
        UUID studentId,
        UUID clubId,
        String clubName,
        String status,
        String message
) {}
