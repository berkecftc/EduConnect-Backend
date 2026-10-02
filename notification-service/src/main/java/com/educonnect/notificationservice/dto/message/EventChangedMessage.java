package com.educonnect.notificationservice.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventChangedMessage(UUID eventId, String title, String clubName, String kind, LocalDateTime startsAt,
                                  LocalDateTime endsAt, String location, String reason, List<UUID> recipientIds) {
}
