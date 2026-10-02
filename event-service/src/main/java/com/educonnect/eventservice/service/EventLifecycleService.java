package com.educonnect.eventservice.service;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(EventLifecycleService.class);
    private static final List<EventStatus> AWAITING_APPROVAL = List.of(EventStatus.PENDING_PRESIDENT, EventStatus.PENDING);

    private final EventRepository eventRepository;
    private final EventParticipationRequestRepository requestRepository;
    private final EventCaches eventCaches;
    private final EventSchedule schedule;

    public EventLifecycleService(EventRepository eventRepository,
                                 EventParticipationRequestRepository requestRepository,
                                 EventCaches eventCaches,
                                 EventSchedule schedule) {
        this.eventRepository = eventRepository;
        this.requestRepository = requestRepository;
        this.eventCaches = eventCaches;
        this.schedule = schedule;
    }

    @Scheduled(cron = "${educonnect.event.lifecycle-cron:0 */10 * * * *}")
    public void advanceScheduled() {
        advance(schedule.now());
    }

    @Transactional
    public void advance(LocalDateTime now) {
        List<Event> finished = eventRepository.findByStatusAndEndsAtBefore(EventStatus.ACTIVE, now);
        for (Event event : finished) {
            event.setStatus(EventStatus.COMPLETED);
            for (EventParticipationRequest request : requestRepository.findByEventIdAndStatus(event.getId(), ParticipationRequestStatus.PENDING)) {
                request.setStatus(ParticipationRequestStatus.CLOSED);
                request.setProcessedDate(now);
                requestRepository.save(request);
            }
        }
        List<Event> missed = eventRepository.findByStatusInAndStartsAtBefore(AWAITING_APPROVAL, now);
        for (Event event : missed) {
            event.setStatus(EventStatus.REJECTED);
            event.setRejectionReason("Etkinlik saati onaylanmadan geçti.");
        }
        eventRepository.saveAll(finished);
        eventRepository.saveAll(missed);
        eventCaches.evictEvents(finished);
        eventCaches.evictEvents(missed);
        if (!finished.isEmpty() || !missed.isEmpty()) {
            log.info("Event lifecycle advanced: completed={}, expired={}", finished.size(), missed.size());
        }
    }
}
