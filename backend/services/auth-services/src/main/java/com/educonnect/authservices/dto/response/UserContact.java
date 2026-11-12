package com.educonnect.authservices.dto.response;

import java.util.UUID;

public record UserContact(UUID id, String email, boolean active) {
}
