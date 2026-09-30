package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubAnnouncement;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(UUID id,
                                   UUID clubId,
                                   String title,
                                   String body,
                                   UUID preparedBy,
                                   Instant createdAt,
                                   Instant publishedAt) {

    public static AnnouncementResponse of(ClubAnnouncement announcement) {
        if (announcement == null) {
            return null;
        }
        return new AnnouncementResponse(announcement.getId(), announcement.getClubId(), announcement.getTitle(),
                announcement.getBody(), announcement.getPreparedBy(), announcement.getCreatedAt(),
                announcement.getPublishedAt());
    }
}
