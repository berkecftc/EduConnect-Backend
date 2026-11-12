package com.educonnect.postservice.event;

import java.util.UUID;

public record UserDeletedMessage(UUID userId, String userType, String reason) {
}
