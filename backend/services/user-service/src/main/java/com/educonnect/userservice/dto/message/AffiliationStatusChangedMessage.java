package com.educonnect.userservice.dto.message;

import java.time.LocalDate;
import java.util.UUID;

public record AffiliationStatusChangedMessage(UUID userId, String affiliation, String status, boolean ended,
                                              boolean accountClosing, LocalDate effectiveDate, String reason) {
}
