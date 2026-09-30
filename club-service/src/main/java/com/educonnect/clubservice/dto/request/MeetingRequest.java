package com.educonnect.clubservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MeetingRequest(@NotNull @PastOrPresent LocalDateTime meetingAt,
                             @Size(max = 200) String location,
                             @NotBlank @Size(max = 5000) String agenda,
                             @NotBlank @Size(max = 20000) String minutes,
                             @NotEmpty @Size(max = 50) Set<@NotNull UUID> attendeeIds,
                             @Size(max = 30) List<@NotBlank @Size(max = 2000) String> decisions,
                             @Size(max = 1000) String note) {
}
