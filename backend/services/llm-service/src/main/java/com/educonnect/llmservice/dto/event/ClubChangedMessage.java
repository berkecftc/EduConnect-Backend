package com.educonnect.llmservice.dto.event;

import java.util.UUID;

public record ClubChangedMessage(UUID clubId, String newName, String newLogoUrl) {
}
