package com.educonnect.eventservice;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.service.EventLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EventIntegrationTest
class EventLifecycleTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventParticipationRequestRepository requestRepository;

    @Autowired
    private EventLifecycleService lifecycleService;

    @Test
    void finishedEventsCompleteAndMissedApprovalsExpire() {
        LocalDateTime now = LocalDateTime.now();
        Event finished = event(EventStatus.ACTIVE, now.minusHours(3), now.minusHours(1));
        Event running = event(EventStatus.ACTIVE, now.minusHours(1), now.plusHours(1));
        Event missed = event(EventStatus.PENDING, now.minusMinutes(5), now.plusHours(1));
        Event waitingPresident = event(EventStatus.PENDING_PRESIDENT, now.minusMinutes(5), now.plusHours(1));
        Event upcoming = event(EventStatus.PENDING, now.plusDays(1), now.plusDays(1).plusHours(2));
        UUID lateRequest = requestRepository.save(new EventParticipationRequest(finished.getId(), UUID.randomUUID())).getId();

        lifecycleService.advance(now);

        assertThat(status(finished)).isEqualTo(EventStatus.COMPLETED);
        assertThat(status(running)).isEqualTo(EventStatus.ACTIVE);
        assertThat(status(missed)).isEqualTo(EventStatus.REJECTED);
        assertThat(eventRepository.findById(missed.getId()).orElseThrow().getRejectionReason()).contains("onaylanmadan");
        assertThat(status(waitingPresident)).isEqualTo(EventStatus.REJECTED);
        assertThat(status(upcoming)).isEqualTo(EventStatus.PENDING);
        assertThat(requestRepository.findById(lateRequest).orElseThrow().getStatus()).isEqualTo(ParticipationRequestStatus.CLOSED);
    }

    private EventStatus status(Event event) {
        return eventRepository.findById(event.getId()).orElseThrow().getStatus();
    }

    private Event event(EventStatus status, LocalDateTime startsAt, LocalDateTime endsAt) {
        Event event = new Event();
        event.setTitle("Zaman Etkinliği");
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        event.setClubId(UUID.randomUUID());
        event.setClubName("Kulüp");
        event.setStatus(status);
        return eventRepository.save(event);
    }
}
