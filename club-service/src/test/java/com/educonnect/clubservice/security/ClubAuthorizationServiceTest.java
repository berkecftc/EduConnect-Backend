package com.educonnect.clubservice.security;

import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClubAuthorizationServiceTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID advisorId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private ClubRepository clubRepository;
    private ClubMembershipRepository membershipRepository;
    private ClubAuthorizationService service;

    @BeforeEach
    void setUp() {
        clubRepository = mock(ClubRepository.class);
        membershipRepository = mock(ClubMembershipRepository.class);
        service = new ClubAuthorizationService(clubRepository, membershipRepository);

        Club club = new Club();
        club.setId(clubId);
        club.setAcademicAdvisorId(advisorId);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(membershipRepository.findByClubIdAndStudentId(any(), any())).thenReturn(Optional.empty());
        when(membershipRepository.findByClubIdAndClubRoleAndIsActive(eq(clubId), eq(ClubPosition.PRESIDENT), eq(true)))
                .thenReturn(List.of(new ClubMembership(clubId, UUID.randomUUID(), ClubPosition.PRESIDENT)));
    }

    private void givenPosition(ClubPosition position, boolean active) {
        ClubMembership membership = new ClubMembership(clubId, userId, position);
        membership.setActive(active);
        when(membershipRepository.findByClubIdAndStudentId(clubId, userId)).thenReturn(Optional.of(membership));
    }

    @Test
    void presidentCanDoEverythingExceptAdvise() {
        givenPosition(ClubPosition.PRESIDENT, true);

        ClubAccess access = service.accessOf(clubId, userId);

        assertThat(access.actingPresident()).isTrue();
        assertThat(access.permissions()).containsExactlyInAnyOrder(
                ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA,
                ClubPermission.MANAGE_MEMBERSHIP_REQUESTS, ClubPermission.PROPOSE_POSITION_CHANGE,
                ClubPermission.UPDATE_CLUB_PROFILE, ClubPermission.PREPARE_PROFILE_CHANGE, ClubPermission.PREPARE_LOGO_CHANGE,
                ClubPermission.CREATE_EVENT,
                ClubPermission.MANAGE_EVENT_OPERATIONS, ClubPermission.PROPOSE_ADVISOR_CHANGE,
                ClubPermission.APPROVE_AS_PRESIDENT, ClubPermission.REQUEST_CLUB_CLOSURE,
                ClubPermission.PREPARE_EVENT, ClubPermission.VIEW_DECISIONS, ClubPermission.PREPARE_ANNOUNCEMENT);
    }

    @Test
    void vicePresidentActsOnlyWhenPresidencyIsVacant() {
        givenPosition(ClubPosition.VICE_PRESIDENT, true);
        assertThat(service.accessOf(clubId, userId).has(ClubPermission.CREATE_EVENT)).isFalse();

        when(membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true))
                .thenReturn(List.of());
        ClubAccess acting = service.accessOf(clubId, userId);

        assertThat(acting.actingPresident()).isTrue();
        assertThat(acting.has(ClubPermission.PROPOSE_POSITION_CHANGE)).isTrue();
    }

    @Test
    void generalSecretaryManagesMembershipButNotPositions() {
        givenPosition(ClubPosition.GENERAL_SECRETARY, true);

        ClubAccess access = service.accessOf(clubId, userId);

        assertThat(access.has(ClubPermission.MANAGE_MEMBERSHIP_REQUESTS)).isTrue();
        assertThat(access.has(ClubPermission.MANAGE_EVENT_OPERATIONS)).isTrue();
        assertThat(access.has(ClubPermission.PROPOSE_POSITION_CHANGE)).isFalse();
        assertThat(access.has(ClubPermission.CREATE_EVENT)).isFalse();
    }

    @Test
    void boardMemberAndTreasurerRunEventOperationsOnly() {
        givenPosition(ClubPosition.TREASURER, true);

        ClubAccess access = service.accessOf(clubId, userId);

        assertThat(access.permissions()).containsExactlyInAnyOrder(
                ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA, ClubPermission.MANAGE_EVENT_OPERATIONS,
                ClubPermission.VIEW_DECISIONS, ClubPermission.PREPARE_ANNOUNCEMENT);
    }

    @Test
    void eventPreparationFollowsTheClubCatalog() {
        for (ClubPosition position : List.of(ClubPosition.VICE_PRESIDENT, ClubPosition.GENERAL_SECRETARY,
                ClubPosition.BOARD_MEMBER)) {
            givenPosition(position, true);
            assertThat(service.accessOf(clubId, userId).has(ClubPermission.PREPARE_EVENT)).as(position.name()).isTrue();
        }
        givenPosition(ClubPosition.TREASURER, true);
        assertThat(service.accessOf(clubId, userId).has(ClubPermission.PREPARE_EVENT)).isFalse();
        givenPosition(ClubPosition.MEMBER, true);
        assertThat(service.accessOf(clubId, userId).has(ClubPermission.PREPARE_EVENT)).isFalse();
    }

    @Test
    void memberOnlySeesMembers() {
        givenPosition(ClubPosition.MEMBER, true);

        assertThat(service.accessOf(clubId, userId).permissions()).containsExactly(ClubPermission.VIEW_MEMBERS);
    }

    @Test
    void inactiveMembershipGrantsNothing() {
        givenPosition(ClubPosition.PRESIDENT, false);

        assertThat(service.accessOf(clubId, userId).permissions()).isEmpty();
    }

    @Test
    void aClosedClubIsReadOnlyForEveryone() {
        clubRepository.findById(clubId).orElseThrow().close(advisorId, "Kapandı", Instant.now());
        givenPosition(ClubPosition.PRESIDENT, true);

        assertThat(service.accessOf(clubId, userId).permissions())
                .containsExactlyInAnyOrder(ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA, ClubPermission.VIEW_DECISIONS);
        assertThat(service.accessOf(clubId, advisorId).permissions())
                .containsExactlyInAnyOrder(ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA, ClubPermission.VIEW_DECISIONS);
    }

    @Test
    void advisorCanAdviseAndViewButNotManage() {
        ClubAccess access = service.accessOf(clubId, advisorId);

        assertThat(access.advisor()).isTrue();
        assertThat(access.permissions()).containsExactlyInAnyOrder(
                ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA, ClubPermission.ADVISE, ClubPermission.VIEW_DECISIONS);
    }

    @Test
    void outsiderAndAnonymousGetNothing() {
        assertThat(service.accessOf(clubId, UUID.randomUUID()).permissions()).isEmpty();
        assertThat(service.accessOf(clubId, null).permissions()).isEmpty();
        assertThatThrownBy(() -> service.require(clubId, UUID.randomUUID(), ClubPermission.VIEW_MEMBERS))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void unknownClubIsNotFound() {
        UUID unknownClub = UUID.randomUUID();
        when(clubRepository.findById(unknownClub)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accessOf(unknownClub, userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
