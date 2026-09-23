package com.educonnect.notificationservice.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubRoleChangeNotificationMessage(
        UUID targetUserId,
        UUID clubId,
        String clubName,
        UUID affectedStudentId,
        String affectedStudentName,
        String previousRole,
        String newRole,
        String status,
        String message,
        String notificationType
) {}
