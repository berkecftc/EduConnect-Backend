package com.educonnect.eventservice.service;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.model.RegistrationStatus;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
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
    private final EventRegistrationRepository registrationRepository;
    private final EventCaches eventCaches;
    private final EventSchedule schedule;

    public EventLifecycleService(EventRepository eventRepository,
                                 EventParticipationRequestRepository requestRepository,
                                 EventRegistrationRepository registrationRepository,
                                 EventCaches eventCaches,
                                 EventSchedule schedule) {
        this.eventRepository = eventRepository;
        this.requestRepository = requestRepository;
        this.registrationRepository = registrationRepository;
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
            for (ParticipationRequestStatus open : List.of(ParticipationRequestStatus.PENDING, ParticipationRequestStatus.WAITLISTED)) {
                for (EventParticipationRequest request : requestRepository.findByEventIdAndStatus(event.getId(), open)) {
                    request.setStatus(ParticipationRequestStatus.CLOSED);
                    request.setProcessedDate(now);
                    requestRepository.save(request);
                }
            }
            for (EventRegistration absent : registrationRepository.findByEventIdAndStatusAndAttendedFalse(event.getId(), RegistrationStatus.REGISTERED)) {
                absent.setStatus(RegistrationStatus.NO_SHOW);
                registrationRepository.save(absent);
                eventCaches.evictStudentRegistrations(absent.getStudentId());
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
