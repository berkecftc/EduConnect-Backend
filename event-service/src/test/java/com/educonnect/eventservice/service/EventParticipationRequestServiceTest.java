package com.educonnect.eventservice.service;

import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.eventservice.Repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.Repository.EventRegistrationRepository;
import com.educonnect.eventservice.Repository.EventRepository;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.client.UserClient;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.security.EventAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventParticipationRequestServiceTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    private EventParticipationRequestRepository requestRepository;
    private EventRegistrationRepository registrationRepository;
    private ClubClient clubClient;
    private EventAuthorizationService authorizationService;
    private EventParticipationRequestService service;
    private Event event;

    @BeforeEach
    void setUp() {
        requestRepository = mock(EventParticipationRequestRepository.class);
        registrationRepository = mock(EventRegistrationRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        clubClient = mock(ClubClient.class);
        authorizationService = mock(EventAuthorizationService.class);
        service = new EventParticipationRequestService(requestRepository, eventRepository, registrationRepository,
                authorizationService, mock(OutboxPublisher.class), mock(UserClient.class), clubClient,
                mock(EventCaches.class));

        event = new Event();
        event.setId(eventId);
        event.setClubId(clubId);
        event.setStatus(EventStatus.ACTIVE);
        event.setEventTime(LocalDateTime.now().plusDays(3));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(clubClient.isStudentMemberOfClub(clubId, studentId)).thenReturn(true);
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestRequiresClubMembership() {
        when(clubClient.isStudentMemberOfClub(clubId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> service.createParticipationRequest(eventId, studentId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void cannotRequestPendingOrPastEvents() {
        event.setStatus(EventStatus.PENDING);
        assertThatThrownBy(() -> service.createParticipationRequest(eventId, studentId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        event.setStatus(EventStatus.ACTIVE);
        event.setEventTime(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.createParticipationRequest(eventId, studentId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(requestRepository, never()).save(any());
    }

    @Test
    void pendingRequestCannotBeDuplicated() {
        when(requestRepository.findByEventIdAndStudentId(eventId, studentId))
                .thenReturn(Optional.of(new EventParticipationRequest(eventId, studentId)));

        assertThatThrownBy(() -> service.createParticipationRequest(eventId, studentId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void approvalIsRefusedForCancelledOrPastEventOrFormerMember() {
        UUID approverId = UUID.randomUUID();
        EventParticipationRequest pending = new EventParticipationRequest(eventId, studentId);
        UUID requestId = UUID.randomUUID();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pending));
        when(authorizationService.canManageEvent(event, approverId)).thenReturn(true);

        event.setStatus(EventStatus.CANCELLED);
        assertThatThrownBy(() -> service.approveParticipationRequest(requestId, approverId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");

        event.setStatus(EventStatus.ACTIVE);
        event.setEventTime(LocalDateTime.now().minusHours(1));
        assertThatThrownBy(() -> service.approveParticipationRequest(requestId, approverId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");

        event.setEventTime(LocalDateTime.now().plusDays(1));
        when(clubClient.isStudentMemberOfClub(clubId, studentId)).thenReturn(false);
        assertThatThrownBy(() -> service.approveParticipationRequest(requestId, approverId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");

        assertThat(pending.getStatus()).isEqualTo(ParticipationRequestStatus.PENDING);
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void rejectedRequestIsReopened() {
        EventParticipationRequest rejected = new EventParticipationRequest(eventId, studentId);
        rejected.setStatus(ParticipationRequestStatus.REJECTED);
        rejected.setProcessedBy(UUID.randomUUID());
        rejected.setProcessedDate(LocalDateTime.now().minusDays(1));
        rejected.setRejectionReason("Kontenjan");
        when(requestRepository.findByEventIdAndStudentId(eventId, studentId)).thenReturn(Optional.of(rejected));

        EventParticipationRequest result = service.createParticipationRequest(eventId, studentId, "tekrar");

        assertThat(result).isSameAs(rejected);
        assertThat(result.getStatus()).isEqualTo(ParticipationRequestStatus.PENDING);
        assertThat(result.getProcessedBy()).isNull();
        assertThat(result.getProcessedDate()).isNull();
        assertThat(result.getRejectionReason()).isNull();
        assertThat(result.getMessage()).isEqualTo("tekrar");
    }
}
