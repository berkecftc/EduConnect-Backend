package com.educonnect.eventservice.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TicketVerificationResponse(UUID eventId,
                                         UUID studentId,
                                         String firstName,
                                         String lastName,
                                         String studentNumber,
                                         Instant checkedInAt) {
}
