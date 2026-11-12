package com.educonnect.authservices.dto.message;

import java.time.LocalDate;
import java.util.UUID;

public record AffiliationStatusChangedMessage(UUID userId, String affiliation, String status, boolean ended,
                                              boolean accountClosing, LocalDate effectiveDate, String reason) {

    public static final String STUDENT = "STUDENT";
    public static final String STAFF = "STAFF";
}
