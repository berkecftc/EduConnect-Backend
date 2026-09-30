package com.educonnect.eventservice.service;

import com.educonnect.eventservice.dto.response.ClubEventStatistics;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClubEventStatisticsService {

    private static final Set<EventStatus> PENDING = Set.of(EventStatus.PENDING, EventStatus.PENDING_PRESIDENT);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;

    public ClubEventStatisticsService(EventRepository eventRepository, EventRegistrationRepository registrationRepository) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
    }

    public ClubEventStatistics statisticsOf(UUID clubId, LocalDateTime from, LocalDateTime to) {
        List<Event> events = eventRepository.findByClubIdAndEventTimeGreaterThanEqualAndEventTimeLessThan(clubId, from, to);
        List<UUID> held = events.stream()
                .filter(event -> event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.ACTIVE)
                .map(Event::getId)
                .toList();
        long registrations = held.isEmpty() ? 0 : registrationRepository.countByEventIdIn(held);
        long attendances = held.isEmpty() ? 0 : registrationRepository.countByEventIdInAndAttendedTrue(held);
        return new ClubEventStatistics(events.size(),
                count(events, EventStatus.COMPLETED),
                count(events, EventStatus.ACTIVE),
                count(events, EventStatus.CANCELLED),
                count(events, EventStatus.REJECTED),
                events.stream().filter(event -> PENDING.contains(event.getStatus())).count(),
                registrations,
                attendances);
    }

    private static long count(List<Event> events, EventStatus status) {
        return events.stream().filter(event -> event.getStatus() == status).count();
    }
}
