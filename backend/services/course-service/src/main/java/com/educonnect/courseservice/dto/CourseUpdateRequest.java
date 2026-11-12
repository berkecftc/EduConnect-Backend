package com.educonnect.courseservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CourseUpdateRequest(@Size(max = 5000) String description,
                                  @Min(1) @Max(10000) Integer capacity) {
}
