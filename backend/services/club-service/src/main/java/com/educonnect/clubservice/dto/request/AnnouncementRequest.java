package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnnouncementRequest(@NotBlank @Size(max = 200) String title,
                                  @NotBlank @Size(max = 5000) String body) {
}
