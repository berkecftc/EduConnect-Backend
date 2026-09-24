package com.educonnect.clubservice.service;

import com.educonnect.clubservice.Repository.ClubMembershipRepository;
import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.Repository.RoleChangeRequestRepository;
import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.request.CreateRoleChangeRequestDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.RoleChangeRequest;
import com.educonnect.clubservice.model.RoleChangeRequestStatus;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleChangeRequestServiceTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID advisorId = UUID.randomUUID();
    private final UUID presidentId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    private RoleChangeRequestRepository requestRepository;
    private ClubMembershipRepository membershipRepository;
    private ClubAuthorizationService authorizationService;
    private RabbitTemplate rabbitTemplate;
    private ClubManagementStatusPublisher managementStatusPublisher;
    private RoleChangeRequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(RoleChangeRequestRepository.class);
        membershipRepository = mock(ClubMembershipRepository.class);
        ClubRepository clubRepository = mock(ClubRepository.class);
        authorizationService = mock(ClubAuthorizationService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        managementStatusPublisher = mock(ClubManagementStatusPublisher.class);
        service = new RoleChangeRequestService(requestRepository, membershipRepository, clubRepository,
                mock(UserClient.class), rabbitTemplate, authorizationService, mock(ClubCacheEvictor.class), managementStatusPublisher);

        Club club = new Club();
        club.setId(clubId);
        club.setName("Robotik");
        club.setAcademicAdvisorId(advisorId);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(requestRepository.save(any(RoleChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.findByClubIdAndClubRoleAndIsActive(any(), any(), eq(true))).thenReturn(Collections.emptyList());
        when(authorizationService.activeManagementPositionOf(any())).thenReturn(Optional.empty());
    }

    private ClubMembership givenMembership(UUID userId, ClubPosition position) {
        ClubMembership membership = new ClubMembership(clubId, userId, position);
        when(membershipRepository.findByClubIdAndStudentId(clubId, userId)).thenReturn(Optional.of(membership));
        return membership;
    }

    private CreateRoleChangeRequestDTO requestFor(UUID target, ClubPosition position) {
        return new CreateRoleChangeRequestDTO(target.toString(), position);
    }

    @Test
    void onlyActingPresidentCanProposePositionChanges() {
        when(authorizationService.require(clubId, studentId, ClubPermission.PROPOSE_POSITION_CHANGE))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.createRoleChangeRequest(clubId, requestFor(studentId, ClubPosition.BOARD_MEMBER), studentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(requestRepository, never()).save(any());
    }

    @Test
    void revocationBecomesPendingRequestInsteadOfImmediateChange() {
        ClubMembership target = givenMembership(studentId, ClubPosition.VICE_PRESIDENT);

        service.requestRoleRevocation(clubId, studentId, presidentId);

        ArgumentCaptor<RoleChangeRequest> saved = ArgumentCaptor.forClass(RoleChangeRequest.class);
        verify(requestRepository).save(saved.capture());
        assertThat(saved.getValue().getRequestedRole()).isEqualTo(ClubPosition.MEMBER);
        assertThat(saved.getValue().getCurrentRole()).isEqualTo(ClubPosition.VICE_PRESIDENT);
        assertThat(saved.getValue().getStatus()).isEqualTo(RoleChangeRequestStatus.PENDING);
        assertThat(target.getClubRole()).isEqualTo(ClubPosition.VICE_PRESIDENT);
        verify(managementStatusPublisher, never()).publishCurrentStatus(any());
    }

    @Test
    void presidentCannotBeTargetedByClubRequests() {
        givenMembership(studentId, ClubPosition.PRESIDENT);

        assertThatThrownBy(() -> service.requestRoleRevocation(clubId, studentId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void cannotRevokeOwnPosition() {
        assertThatThrownBy(() -> service.requestRoleRevocation(clubId, presidentId, presidentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void boardIsLimitedToSevenMembersIncludingPendingRequests() {
        givenMembership(studentId, ClubPosition.MEMBER);
        List<ClubMembership> sixHolders = Collections.nCopies(6, new ClubMembership(clubId, UUID.randomUUID(), ClubPosition.BOARD_MEMBER));
        when(membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.BOARD_MEMBER, true)).thenReturn(sixHolders);
        when(requestRepository.countByClubIdAndRequestedRoleAndStatus(clubId, ClubPosition.BOARD_MEMBER, RoleChangeRequestStatus.PENDING))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.createRoleChangeRequest(clubId, requestFor(studentId, ClubPosition.BOARD_MEMBER), presidentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void studentCannotHoldManagementPositionsInTwoClubs() {
        givenMembership(studentId, ClubPosition.MEMBER);
        when(authorizationService.activeManagementPositionOf(studentId))
                .thenReturn(Optional.of(new ClubMembership(UUID.randomUUID(), studentId, ClubPosition.TREASURER)));

        assertThatThrownBy(() -> service.createRoleChangeRequest(clubId, requestFor(studentId, ClubPosition.GENERAL_SECRETARY), presidentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void advisorApprovalAppliesDemotionAndClosesTerm() {
        ClubMembership target = givenMembership(studentId, ClubPosition.BOARD_MEMBER);
        RoleChangeRequest request = new RoleChangeRequest(clubId, studentId, ClubPosition.BOARD_MEMBER, ClubPosition.MEMBER, presidentId);
        UUID requestId = UUID.randomUUID();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        service.approveRoleChangeRequest(requestId, advisorId);

        assertThat(target.getClubRole()).isEqualTo(ClubPosition.MEMBER);
        assertThat(target.getTermEndDate()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(RoleChangeRequestStatus.APPROVED);
        verify(managementStatusPublisher).publishCurrentStatus(studentId);
        verify(authorizationService).require(clubId, advisorId, ClubPermission.ADVISE);
    }

    @Test
    void onlyAdvisorCanApprove() {
        RoleChangeRequest request = new RoleChangeRequest(clubId, studentId, ClubPosition.MEMBER, ClubPosition.TREASURER, presidentId);
        UUID requestId = UUID.randomUUID();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(authorizationService.require(clubId, presidentId, ClubPermission.ADVISE))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.approveRoleChangeRequest(requestId, presidentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(request.getStatus()).isEqualTo(RoleChangeRequestStatus.PENDING);
    }

    @Test
    void approvalFailsWhenPositionChangedMeanwhile() {
        givenMembership(studentId, ClubPosition.TREASURER);
        RoleChangeRequest request = new RoleChangeRequest(clubId, studentId, ClubPosition.MEMBER, ClubPosition.BOARD_MEMBER, presidentId);
        UUID requestId = UUID.randomUUID();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approveRoleChangeRequest(requestId, advisorId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void advisorCanRemovePresident() {
        ClubMembership president = new ClubMembership(clubId, presidentId, ClubPosition.PRESIDENT);
        when(membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true))
                .thenReturn(List.of(president));

        service.removePresidentByAdvisor(clubId, advisorId, "Görev ihmali");

        assertThat(president.getClubRole()).isEqualTo(ClubPosition.MEMBER);
        assertThat(president.getTermEndDate()).isNotNull();
        verify(managementStatusPublisher).publishCurrentStatus(presidentId);
    }
}
