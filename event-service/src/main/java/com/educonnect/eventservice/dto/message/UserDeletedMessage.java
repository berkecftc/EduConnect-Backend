package com.educonnect.eventservice.dto.message;

import java.util.UUID;

public record UserDeletedMessage(UUID userId, String userType, String reason) {
}
