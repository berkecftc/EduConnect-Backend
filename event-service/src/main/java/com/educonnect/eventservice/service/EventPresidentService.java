package com.educonnect.eventservice.service;

import com.educonnect.common.web.ConflictException;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EventPresidentService {

    private static final Logger log = LoggerFactory.getLogger(EventPresidentService.class);

    private final EventRepository eventRepository;
    private final EventAuthorizationService eventAuthorizationService;
    private final EventCaches eventCaches;
    private final EventSchedule schedule;
    private final EventNotifier notifier;

    public EventPresidentService(EventRepository eventRepository,
                                 EventAuthorizationService eventAuthorizationService,
                                 EventCaches eventCaches,
                                 EventSchedule schedule,
                                 EventNotifier notifier) {
        this.eventRepository = eventRepository;
        this.eventAuthorizationService = eventAuthorizationService;
        this.eventCaches = eventCaches;
        this.schedule = schedule;
        this.notifier = notifier;
    }

    @Transactional(readOnly = true)
    public List<Event> getPendingEventsForPresident(UUID userId) {
        List<UUID> clubIds = eventAuthorizationService.accessesOf(userId).stream()
                .filter(access -> access.has(EventAuthorizationService.APPROVE_AS_PRESIDENT))
                .map(ClubAccess::clubId)
                .toList();
        if (clubIds.isEmpty()) {
            return List.of();
        }
        return eventRepository.findByClubIdInAndStatus(clubIds, EventStatus.PENDING_PRESIDENT);
    }

    public Event approve(UUID eventId, UUID userId) {
        Event event = pendingForPresident(eventId, userId);
        schedule.requireNotStarted(event);
        event.setStatus(EventStatus.PENDING);
        Event saved = eventRepository.save(event);
        eventCaches.evictEvent(saved);
        notifier.awaitingApproval(saved, userId);
        log.info("Event approved by president and sent to advisor: eventId={}", eventId);
        return saved;
    }

    public Event reject(UUID eventId, UUID userId, String reason) {
        Event event = pendingForPresident(eventId, userId);
        event.setStatus(EventStatus.REJECTED);
        event.setRejectionReason(reason);
        Event saved = eventRepository.save(event);
        eventCaches.evictEvent(saved);
        notifier.decided(saved, false, "kulüp başkanı", reason);
        log.info("Event rejected by president: eventId={}", eventId);
        return saved;
    }

    private Event pendingForPresident(UUID eventId, UUID userId) {
        Event event = EventFinder.require(eventRepository, eventId);
        eventAuthorizationService.require(event.getClubId(), userId, EventAuthorizationService.APPROVE_AS_PRESIDENT);
        if (event.getStatus() != EventStatus.PENDING_PRESIDENT) {
            throw new ConflictException("EVENT_NOT_AWAITING_PRESIDENT", "Etkinlik başkan onayı beklemiyor.");
        }
        return event;
    }
}
