package com.educonnect.clubservice.dto.message;

import java.io.Serializable;
import java.util.UUID;

public record ClubNotificationMessage(UUID targetUserId,
                                      UUID clubId,
                                      String clubName,
                                      String subject,
                                      String message) implements Serializable {
}
