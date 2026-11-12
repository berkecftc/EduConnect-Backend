package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.MaterialKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaterialRequest(@NotBlank @Size(max = 255) String title,
                              @Size(max = 10000) String description,
                              @Size(max = 100) String section,
                              @Min(0) @Max(10000) Integer sortOrder,
                              @NotNull MaterialKind kind,
                              @Size(max = 2000) String linkUrl,
                              Boolean visible) {
}
