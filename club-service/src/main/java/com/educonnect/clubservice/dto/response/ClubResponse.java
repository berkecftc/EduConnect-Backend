package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.Club;

import java.time.Instant;
import java.util.UUID;

public record ClubResponse(UUID id,
                           String name,
                           String about,
                           String logoUrl,
                           UUID academicAdvisorId,
                           Instant createdAt,
                           Instant updatedAt) {

    public static ClubResponse from(Club club) {
        return new ClubResponse(club.getId(), club.getName(), club.getAbout(), club.getLogoUrl(),
                club.getAcademicAdvisorId(), club.getCreatedAt(), club.getUpdatedAt());
    }
}
