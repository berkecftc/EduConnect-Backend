package com.educonnect.eventservice.service;

import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.EventCreatedMessage;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EventAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(EventAdvisorService.class);

    private final EventRepository eventRepository;
    private final OutboxPublisher outboxPublisher;
    private final ClubClient clubClient;
    private final EventAuthorizationService eventAuthorizationService;
    private final EventCaches eventCaches;
    private final EventSchedule schedule;

    public EventAdvisorService(EventRepository eventRepository,
                               OutboxPublisher outboxPublisher,
                               ClubClient clubClient,
                               EventAuthorizationService eventAuthorizationService,
                               EventCaches eventCaches,
                               EventSchedule schedule) {
        this.eventRepository = eventRepository;
        this.outboxPublisher = outboxPublisher;
        this.clubClient = clubClient;
        this.eventAuthorizationService = eventAuthorizationService;
        this.eventCaches = eventCaches;
        this.schedule = schedule;
    }

    public List<Event> getAllEventsForAdvisor(UUID advisorId) {
        List<UUID> clubIds = clubClient.getClubIdsByAdvisorId(advisorId);

        if (clubIds == null || clubIds.isEmpty()) {
            return List.of();
        }

        return eventRepository.findByClubIdIn(clubIds);
    }

    public List<Event> getPendingEventsForAdvisor(UUID advisorId) {
        List<UUID> clubIds = clubClient.getClubIdsByAdvisorId(advisorId);

        if (clubIds == null || clubIds.isEmpty()) {
            return List.of();
        }

        return eventRepository.findByClubIdInAndStatus(clubIds, EventStatus.PENDING);
    }

    public Event approveEvent(UUID eventId, UUID approverId) {
        Event event = EventFinder.require(eventRepository, eventId);

        if (event.getStatus() != EventStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etkinlik bekleyen durumda değil.");
        }

        validateAdvisorAuthorization(event.getClubId(), approverId);
        schedule.requireNotStarted(event);

        boolean firstPublication = event.getPublishedAt() == null;
        event.setStatus(EventStatus.ACTIVE);
        if (firstPublication) {
            event.setPublishedAt(LocalDateTime.now());
        }
        Event savedEvent = eventRepository.save(event);
        eventCaches.evictEvent(savedEvent);
        if (!firstPublication) {
            return savedEvent;
        }

        EventCreatedMessage message = new EventCreatedMessage(
                savedEvent.getId(),
                savedEvent.getTitle(),
                savedEvent.getDescription(),
                savedEvent.getStartsAt(),
                savedEvent.getLocation(),
                savedEvent.getClubId(),
                savedEvent.getClubName()
        );

        outboxPublisher.publish(
                EventRabbitMQConfig.CLUB_EXCHANGE_NAME,
                EventRabbitMQConfig.ROUTING_KEY_EVENT_CREATED,
                message
        );

        log.info("Etkinlik onaylandı ve bildirim gönderildi: {} (Onaylayan: {})", savedEvent.getTitle(), approverId);

        return savedEvent;
    }

    public Event rejectEvent(UUID eventId, UUID rejectorId, String reason) {
        Event event = EventFinder.require(eventRepository, eventId);

        if (event.getStatus() != EventStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sadece bekleyen etkinlikler reddedilebilir.");
        }

        validateAdvisorAuthorization(event.getClubId(), rejectorId);

        event.setStatus(EventStatus.REJECTED);
        event.setRejectionReason(reason);
        log.info("Etkinlik reddedildi: {} (Reddeden: {})", event.getTitle(), rejectorId);

        Event savedEvent = eventRepository.save(event);
        eventCaches.evictEvent(savedEvent);
        return savedEvent;
    }

    private void validateAdvisorAuthorization(UUID clubId, UUID userId) {
        if (!eventAuthorizationService.accessOf(clubId, userId).has(EventAuthorizationService.ADVISE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bu etkinliği onaylama/reddetme yetkiniz yok. Sadece ilgili kulübün danışmanı bu işlemi yapabilir."
            );
        }
    }
}
