package com.educonnect.authservices.dto.message;

import java.util.UUID;

public record UserEmailChangedMessage(UUID userId, String email) {
}
