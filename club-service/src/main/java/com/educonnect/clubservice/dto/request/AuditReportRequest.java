package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.Size;

public record AuditReportRequest(@Size(max = 20000) String findings,
                                 @Size(max = 5000) String recommendations) {
}
