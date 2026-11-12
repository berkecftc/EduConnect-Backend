package com.educonnect.courseservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MaterialUpdateRequest(@Size(min = 1, max = 255) String title,
                                    @Size(max = 10000) String description,
                                    @Size(max = 100) String section,
                                    @Min(0) @Max(10000) Integer sortOrder,
                                    @Size(max = 2000) String linkUrl,
                                    Boolean visible) {
}
