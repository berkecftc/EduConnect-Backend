package com.educonnect.clubservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record PositionChanged(UUID clubId,
                              UUID studentId,
                              ClubPosition from,
                              ClubPosition to,
                              LocalDateTime at,
                              PositionEndReason reason) {
}
