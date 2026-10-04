package com.educonnect.eventservice.service;

import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.config.ApprovalChainSettings;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceAuthorizationTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private EventRegistrationRepository registrationRepository;
    private EventAuthorizationService authorizationService;
    private EventService service;
    private Event event;

    @BeforeEach
    void setUp() {
        EventRepository eventRepository = mock(EventRepository.class);
        registrationRepository = mock(EventRegistrationRepository.class);
        authorizationService = mock(EventAuthorizationService.class);
        service = new EventService(eventRepository, mock(MinioService.class), registrationRepository,
                mock(ClubClient.class), authorizationService, mock(EventCaches.class), new ApprovalChainSettings(false),
                new EventSchedule(Duration.ZERO, Duration.ofMinutes(60), Duration.ofHours(2), Clock.systemDefaultZone()),
                mock(EventNotifier.class));

        event = new Event();
        event.setId(eventId);
        event.setClubId(clubId);
        event.setStatus(EventStatus.ACTIVE);
        event.setStartsAt(LocalDateTime.now().plusDays(3));
        event.setEndsAt(event.getStartsAt().plusHours(2));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    }

    @Test
    void ticketCheckInRequiresEventManagerOrAssignedStaff() {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(eventId);
        registration.setQrCode("ticket");
        when(registrationRepository.findByQrCode("ticket")).thenReturn(Optional.of(registration));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(authorizationService).requireCheckInStaff(event, userId);

        assertThatThrownBy(() -> service.verifyTicket("ticket", userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(registration.isAttended()).isFalse();
    }
}
