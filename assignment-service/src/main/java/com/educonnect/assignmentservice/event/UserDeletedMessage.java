package com.educonnect.assignmentservice.event;

import java.util.UUID;

public record UserDeletedMessage(UUID userId, String userType, String reason) {
}
