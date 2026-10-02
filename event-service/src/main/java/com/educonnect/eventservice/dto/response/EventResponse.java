package com.educonnect.eventservice.dto.response;

import com.educonnect.eventservice.model.AdmissionMode;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventAudience;
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
                            EventAudience audience,
                            AdmissionMode admission,
                            Integer capacity,
                            LocalDateTime registrationOpensAt,
                            LocalDateTime registrationClosesAt,
                            LocalDateTime cancelUntil,
                            String location,
                            String imageUrl,
                            UUID clubId,
                            String clubName,
                            EventStatus status,
                            Instant createdAt,
                            Instant updatedAt,
                            String rejectionReason,
                            String cancellationReason) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getStartsAt(),
                event.getStartsAt(), event.getEndsAt(), event.getSpeakers(),
                event.getAudience(), event.getAdmission(), event.getCapacity(), event.getRegistrationOpensAt(),
                event.getStartsAt() == null ? null : event.effectiveRegistrationClose(),
                event.getStartsAt() == null ? null : event.effectiveCancelUntil(),
                event.getLocation(), event.getImageUrl(), event.getClubId(), event.getClubName(), event.getStatus(),
                event.getCreatedAt(), event.getUpdatedAt(), event.getRejectionReason(), event.getCancellationReason());
    }

    public static List<EventResponse> from(List<Event> events) {
        return events.stream().map(EventResponse::from).toList();
    }
}
