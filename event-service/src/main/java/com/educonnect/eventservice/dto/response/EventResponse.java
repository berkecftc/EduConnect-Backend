package com.educonnect.eventservice.dto.response;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventResponse(UUID id,
                            String title,
                            String description,
                            LocalDateTime eventTime,
                            LocalDateTime startsAt,
                            LocalDateTime endsAt,
                            String speakers,
                            String location,
                            String imageUrl,
                            UUID clubId,
                            String clubName,
                            EventStatus status,
                            Instant createdAt,
                            Instant updatedAt,
                            String rejectionReason) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getStartsAt(),
                event.getStartsAt(), event.getEndsAt(), event.getSpeakers(),
                event.getLocation(), event.getImageUrl(), event.getClubId(), event.getClubName(), event.getStatus(),
                event.getCreatedAt(), event.getUpdatedAt(), event.getRejectionReason());
    }

    public static List<EventResponse> from(List<Event> events) {
        return events.stream().map(EventResponse::from).toList();
    }
}
