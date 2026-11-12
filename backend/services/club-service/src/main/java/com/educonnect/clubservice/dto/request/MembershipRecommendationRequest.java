package com.educonnect.clubservice.dto.request;

import com.educonnect.clubservice.model.MembershipRecommendation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MembershipRecommendationRequest(
        @NotNull(message = "Öneri zorunludur") MembershipRecommendation recommendation,
        @Size(max = 1000, message = "Not en fazla 1000 karakter olabilir") String note) {
}
