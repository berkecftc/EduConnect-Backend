package com.educonnect.clubservice.service;

import com.educonnect.clubservice.Repository.ArchivedClubRepository;
import com.educonnect.clubservice.Repository.ClubCreationRequestRepository;
import com.educonnect.clubservice.Repository.ClubMembershipRepository;
import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.client.UserLookup;
import com.educonnect.clubservice.dto.request.SubmitClubRequest;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClubCreationRequestTest {

    private final UUID requesterId = UUID.randomUUID();
    private final UUID advisorId = UUID.randomUUID();

    private ClubCreationRequestRepository requestRepository;
    private UserClient userClient;
    private ClubService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(ClubCreationRequestRepository.class);
        userClient = mock(UserClient.class);
        service = new ClubService(mock(ClubRepository.class), mock(ClubMembershipRepository.class),
                mock(OutboxPublisher.class), mock(MinioService.class), requestRepository, userClient,
                mock(ArchivedClubRepository.class), mock(ClubAuthorizationService.class), mock(ClubCacheEvictor.class),
                mock(ClubManagementStatusPublisher.class), mock(ClubNotificationPublisher.class), mock(UserLookup.class));
    }

    @Test
    void nonStudentCannotRequestClubCreation() {
        assertThatThrownBy(() -> service.submitClubCreationRequest(request(advisorId), requesterId, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void requesterCannotBeTheAdvisor() {
        assertThatThrownBy(() -> service.submitClubCreationRequest(request(requesterId), requesterId, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void selfAdvisedPendingRequestCannotBeApproved() {
        ClubCreationRequest pending = new ClubCreationRequest();
        pending.setClubName("Test Kulübü");
        pending.setRequestingStudentId(requesterId);
        pending.setSuggestedAdvisorId(requesterId);
        pending.setStatus(ClubCreationRequestStatus.PENDING);
        UUID requestId = UUID.randomUUID();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approveClubCreationRequestByAdvisor(requestId, requesterId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(pending.getStatus()).isEqualTo(ClubCreationRequestStatus.PENDING);
    }

    private static SubmitClubRequest request(UUID advisor) {
        SubmitClubRequest request = new SubmitClubRequest();
        request.setName("Test Kulübü");
        request.setAcademicAdvisorId(advisor);
        return request;
    }
}
