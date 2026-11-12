package com.educonnect.courseservice.dto.message;

import java.util.UUID;

public record UserDeletedMessage(UUID userId, String userType, String reason) {
}
