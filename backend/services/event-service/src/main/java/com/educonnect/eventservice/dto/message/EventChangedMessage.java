package com.educonnect.eventservice.dto.message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventChangedMessage(UUID eventId, String title, String clubName, String kind, LocalDateTime startsAt,
                                  LocalDateTime endsAt, String location, String reason, List<UUID> recipientIds) {
}
