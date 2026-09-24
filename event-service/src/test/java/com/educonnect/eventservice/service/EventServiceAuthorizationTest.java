package com.educonnect.eventservice.service;

import com.educonnect.eventservice.Repository.EventRegistrationRepository;
import com.educonnect.eventservice.Repository.EventRepository;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventServiceAuthorizationTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private EventRepository eventRepository;
    private EventRegistrationRepository registrationRepository;
    private ClubClient clubClient;
    private EventAuthorizationService authorizationService;
    private EventService service;
    private Event event;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        registrationRepository = mock(EventRegistrationRepository.class);
        clubClient = mock(ClubClient.class);
        authorizationService = mock(EventAuthorizationService.class);
        service = new EventService(eventRepository, mock(MinioService.class), mock(RabbitTemplate.class),
                registrationRepository, mock(RestTemplate.class), mock(UserClient.class), clubClient, authorizationService);

        event = new Event();
        event.setId(eventId);
        event.setClubId(clubId);
        event.setStatus(EventStatus.ACTIVE);
        event.setEventTime(LocalDateTime.now().plusDays(3));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    }

    @Test
    void pendingEventIsHiddenFromPublic() {
        event.setStatus(EventStatus.PENDING);

        assertThatThrownBy(() -> service.getEventDetailsForViewer(eventId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void pendingEventIsVisibleToClubManagement() {
        event.setStatus(EventStatus.PENDING);
        when(authorizationService.canViewEventInternals(event, userId)).thenReturn(true);

        assertThat(service.getEventDetailsForViewer(eventId, userId)).isSameAs(event);
    }

    @Test
    void activeEventIsPublic() {
        assertThat(service.getEventDetailsForViewer(eventId, null)).isSameAs(event);
    }

    @Test
    void registrantsRequireClubAccessEvenForRepeatedCalls() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(authorizationService).requireEventViewer(event, userId);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.getEventRegistrantsWithUserInfo(eventId, userId))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("403");
        }
        verify(registrationRepository, never()).findByEventId(any());
    }

    @Test
    void registrantListDoesNotExposeTickets() {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(eventId);
        registration.setStudentId(UUID.randomUUID());
        registration.setQrCode("secret-ticket");
        when(registrationRepository.findByEventId(eventId)).thenReturn(List.of(registration));

        assertThat(service.getEventRegistrantsWithUserInfo(eventId, userId))
                .singleElement()
                .satisfies(dto -> assertThat(dto.getQrCode()).isNull());
    }

    @Test
    void ticketCheckInRequiresEventManager() {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(eventId);
        registration.setQrCode("ticket");
        when(registrationRepository.findByQrCode("ticket")).thenReturn(Optional.of(registration));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(authorizationService).requireEventManager(event, userId);

        assertThatThrownBy(() -> service.verifyTicket("ticket", userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(registration.isAttended()).isFalse();
    }

    @Test
    void registrationRequiresClubMembership() {
        when(clubClient.isStudentMemberOfClub(clubId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.registerForEvent(eventId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void cannotRegisterForPendingOrPastEvents() {
        event.setStatus(EventStatus.PENDING);
        assertThatThrownBy(() -> service.registerForEvent(eventId, userId)).isInstanceOf(IllegalStateException.class);

        event.setStatus(EventStatus.ACTIVE);
        event.setEventTime(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.registerForEvent(eventId, userId)).isInstanceOf(IllegalStateException.class);
    }
}
