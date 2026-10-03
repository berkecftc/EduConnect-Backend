package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.request.CreateRoleChangeRequestDTO;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;

import java.time.Instant;
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

    private ClubApprovalRequestRepository approvalRepository;
    private ClubMembershipRepository membershipRepository;
    private ClubAuthorizationService authorizationService;
    private OutboxPublisher outboxPublisher;
    private ClubManagementStatusPublisher managementStatusPublisher;
    private ClubDecisionLog decisionLog;
    private RoleChangeRequestService service;
    private RoleChangeDecisionService decisionService;

    @BeforeEach
    void setUp() {
        approvalRepository = mock(ClubApprovalRequestRepository.class);
        membershipRepository = mock(ClubMembershipRepository.class);
        ClubRepository clubRepository = mock(ClubRepository.class);
        authorizationService = mock(ClubAuthorizationService.class);
        outboxPublisher = mock(OutboxPublisher.class);
        managementStatusPublisher = mock(ClubManagementStatusPublisher.class);
        decisionLog = mock(ClubDecisionLog.class);
        ClubLeadershipService leadershipService = mock(ClubLeadershipService.class);
        when(leadershipService.currentLeaderOf(clubId)).thenReturn(Optional.of(presidentId));
        UserClient userClient = mock(UserClient.class);
        RoleChangeUserNames userNames = new RoleChangeUserNames(userClient);
        RoleChangeRequestMapper mapper = new RoleChangeRequestMapper(userNames);
        RoleChangeNotifier notifier = new RoleChangeNotifier(new ClubNotificationPublisher(outboxPublisher));
        ClubPositionRules positionRules = new ClubPositionRules(membershipRepository, approvalRepository, authorizationService);
        RoleChangeApprovalHandler handler = new RoleChangeApprovalHandler(membershipRepository, positionRules,
                mock(ClubCacheEvictor.class), managementStatusPublisher, leadershipService, notifier, userNames,
                new MembershipTerms("09-30", 30));
        ClubApprovalEngine engine = new ClubApprovalEngine(approvalRepository, clubRepository, authorizationService,
                leadershipService, decisionLog, List.of(handler));
        service = new RoleChangeRequestService(approvalRepository, membershipRepository, clubRepository,
                userClient, authorizationService, positionRules, mapper, engine);
        decisionService = new RoleChangeDecisionService(approvalRepository, membershipRepository, clubRepository,
                authorizationService, mock(ClubCacheEvictor.class), managementStatusPublisher, notifier, userNames,
                mapper, leadershipService, engine, decisionLog, new MembershipTerms("09-30", 30));

        Club club = new Club();
        club.setId(clubId);
        club.setName("Robotik");
        club.setAcademicAdvisorId(advisorId);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(approvalRepository.save(any(ClubApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

    private ClubApprovalRequest pendingRequest(UUID requestId, ClubPosition from, ClubPosition to) {
        ClubApprovalRequest request = new ClubApprovalRequest(clubId, ApprovalType.ROLE_CHANGE, presidentId, studentId,
                from, to, null, Instant.now());
        when(approvalRepository.findById(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void onlyActingPresidentCanProposePositionChanges() {
        when(authorizationService.require(clubId, studentId, ClubPermission.PROPOSE_POSITION_CHANGE))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.createRoleChangeRequest(clubId, requestFor(studentId, ClubPosition.BOARD_MEMBER), studentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(approvalRepository, never()).save(any());
    }

    @Test
    void revocationBecomesPendingRequestInsteadOfImmediateChange() {
        ClubMembership target = givenMembership(studentId, ClubPosition.VICE_PRESIDENT);

        service.requestRoleRevocation(clubId, studentId, presidentId);

        ArgumentCaptor<ClubApprovalRequest> saved = ArgumentCaptor.forClass(ClubApprovalRequest.class);
        verify(approvalRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ApprovalType.ROLE_CHANGE);
        assertThat(saved.getValue().getRequestedPosition()).isEqualTo(ClubPosition.MEMBER);
        assertThat(saved.getValue().getCurrentPosition()).isEqualTo(ClubPosition.VICE_PRESIDENT);
        assertThat(saved.getValue().getStatus()).isEqualTo(ApprovalStatus.PENDING_ADVISOR);
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
        when(approvalRepository.countByClubIdAndTypeAndRequestedPositionAndStatusIn(clubId, ApprovalType.ROLE_CHANGE,
                ClubPosition.BOARD_MEMBER, ApprovalStatus.PENDING)).thenReturn(1L);

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
        UUID requestId = UUID.randomUUID();
        ClubApprovalRequest request = pendingRequest(requestId, ClubPosition.BOARD_MEMBER, ClubPosition.MEMBER);

        decisionService.approveRoleChangeRequest(requestId, advisorId);

        assertThat(target.getClubRole()).isEqualTo(ClubPosition.MEMBER);
        assertThat(target.getTermEndDate()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(request.getDecidedBy()).isEqualTo(advisorId);
        verify(managementStatusPublisher).publishCurrentStatus(studentId);
    }

    @Test
    void onlyAdvisorCanApprove() {
        UUID requestId = UUID.randomUUID();
        ClubApprovalRequest request = pendingRequest(requestId, ClubPosition.MEMBER, ClubPosition.TREASURER);

        assertThatThrownBy(() -> decisionService.approveRoleChangeRequest(requestId, presidentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING_ADVISOR);
    }

    @Test
    void approvalFailsWhenPositionChangedMeanwhile() {
        givenMembership(studentId, ClubPosition.TREASURER);
        UUID requestId = UUID.randomUUID();
        pendingRequest(requestId, ClubPosition.MEMBER, ClubPosition.BOARD_MEMBER);

        assertThatThrownBy(() -> decisionService.approveRoleChangeRequest(requestId, advisorId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void advisorCanRemovePresident() {
        ClubMembership president = new ClubMembership(clubId, presidentId, ClubPosition.PRESIDENT);
        when(membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true))
                .thenReturn(List.of(president));

        decisionService.removePresidentByAdvisor(clubId, advisorId, "Görev ihmali");

        assertThat(president.getClubRole()).isEqualTo(ClubPosition.MEMBER);
        assertThat(president.getTermEndDate()).isNotNull();
        verify(managementStatusPublisher).publishCurrentStatus(presidentId);
    }

    @Test
    void roleChangeRequestsNotifyTheDeciderThroughTheNotificationCenter() {
        givenMembership(studentId, ClubPosition.MEMBER);

        service.createRoleChangeRequest(clubId, requestFor(studentId, ClubPosition.TREASURER), presidentId);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(NotificationRequest.EXCHANGE), eq(NotificationRequest.ROUTING_KEY), payload.capture());
        NotificationRequest message = (NotificationRequest) payload.getValue();
        assertThat(message.recipientIds()).containsExactly(advisorId);
        assertThat(message.category()).isEqualTo(NotificationCategory.CLUB_MANAGEMENT);
        assertThat(message.type()).isEqualTo("CLUB_ROLE_CHANGE");
        assertThat(message.title()).endsWith("Görev değişikliği talebi");
        assertThat(message.link()).isEqualTo("/clubs/" + clubId);
    }
}
