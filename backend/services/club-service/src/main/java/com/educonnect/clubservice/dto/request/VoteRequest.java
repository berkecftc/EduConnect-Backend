package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.ElectionBallot;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record VoteRequest(@NotNull ElectionBallot ballot,
                          @NotEmpty @Size(max = 6) Set<@NotNull UUID> candidateIds) {
}
