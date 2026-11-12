package com.educonnect.clubservice.model;

import java.time.Instant;
import java.util.UUID;

public record PositionChanged(UUID clubId,
                              UUID studentId,
                              ClubPosition from,
                              ClubPosition to,
                              Instant at,
                              PositionEndReason reason) {
}
