package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.ElectionBallot;
import jakarta.validation.constraints.NotNull;

public record CandidacyRequest(@NotNull ElectionBallot ballot) {
}
