package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record OpenElectionRequest(@Min(0) @Max(6) int boardSeats,
                                  @Min(0) @Max(3) int auditSeats,
                                  @Size(max = 1000) String note) {
}
