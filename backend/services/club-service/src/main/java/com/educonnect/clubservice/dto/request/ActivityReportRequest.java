package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.Size;

public record ActivityReportRequest(@Size(max = 20000) String summary) {
}
