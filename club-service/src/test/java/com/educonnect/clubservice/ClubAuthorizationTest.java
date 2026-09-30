package com.educonnect.clubservice;

import com.educonnect.clubservice.repository.ClubCreationRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubMembershipRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubMembershipRequest;
import com.educonnect.clubservice.model.ClubNames;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.model.MembershipRequestStatus;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubAuthorizationTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private final UUID president = UUID.randomUUID();
    private final UUID vicePresident = UUID.randomUUID();
    private final UUID secretary = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();
    private final UUID otherPresident = UUID.randomUUID();
    private final UUID otherOfficer = UUID.randomUUID();
    private final UUID otherAdvisor = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @Autowired
    private ClubMembershipRequestRepository membershipRequestRepository;

    @Autowired
    private ClubApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private ClubCreationRequestRepository creationRequestRepository;

    private UUID clubId;
    private UUID otherClubId;

    @BeforeEach
    void seedClubs() {
        clubId = club(advisor);
        seat(clubId, president, ClubPosition.PRESIDENT);
        seat(clubId, vicePresident, ClubPosition.VICE_PRESIDENT);
        seat(clubId, secretary, ClubPosition.GENERAL_SECRETARY);
        seat(clubId, officer, ClubPosition.BOARD_MEMBER);
        seat(clubId, member, ClubPosition.MEMBER);

        otherClubId = club(otherAdvisor);
        seat(otherClubId, otherPresident, ClubPosition.PRESIDENT);
        seat(otherClubId, otherOfficer, ClubPosition.TREASURER);
    }

    @Test
    void ordinaryMembersAreListedOnlyForMembersAndTheAdvisor() throws Exception {
        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(4));
        details(TestTokens.student(student)).andExpect(jsonPath("$.members.length()").value(4));
        details(TestTokens.student(otherPresident)).andExpect(jsonPath("$.members.length()").value(4));
        details(TestTokens.academician(otherAdvisor)).andExpect(jsonPath("$.members.length()").value(4));
        details(TestTokens.admin(admin)).andExpect(jsonPath("$.members.length()").value(4));
        details(TestTokens.student(member)).andExpect(jsonPath("$.members.length()").value(5));
        details(TestTokens.academician(advisor)).andExpect(jsonPath("$.members.length()").value(5));
    }

    @Test
    void pendingMembershipRequestsAreVisibleOnlyToThePresidentAndTheSecretary() throws Exception {
        String path = "/api/clubs/{clubId}/membership-requests/pending";
        for (String token : new String[]{TestTokens.student(member), TestTokens.student(officer),
                TestTokens.academician(advisor), TestTokens.student(otherPresident), TestTokens.admin(admin)}) {
            mockMvc.perform(as(get(path, clubId), token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }
        mockMvc.perform(as(get(path, clubId), TestTokens.student(president)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get(path, clubId), TestTokens.student(secretary)))
                .andExpect(status().isOk());
    }

    @Test
    void pendingMembershipCountIsManagementData() throws Exception {
        membershipRequest(student);
        String path = "/api/clubs/{clubId}/membership-requests/pending/count";
        for (String token : new String[]{TestTokens.student(member), TestTokens.student(student),
                TestTokens.student(otherPresident), TestTokens.admin(admin)}) {
            mockMvc.perform(as(get(path, clubId), token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(as(get(path, clubId), TestTokens.student(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(as(get(path, clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void onlyStudentsRequestMembership() throws Exception {
        String path = "/api/clubs/{clubId}/membership-requests";
        mockMvc.perform(as(post(path, clubId), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(post(path, clubId), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        assertThat(membershipRequestRepository.findByClubIdAndStatus(clubId, MembershipRequestStatus.PENDING)).isEmpty();

        mockMvc.perform(as(post(path, clubId), TestTokens.student(student)))
                .andExpect(status().isCreated());
        assertThat(membershipRequestRepository.existsByClubIdAndStudentIdAndStatus(
                clubId, student, MembershipRequestStatus.PENDING)).isTrue();
    }

    @Test
    void membershipRequestsAreApprovedOnlyByThisClubsPresident() throws Exception {
        UUID requestId = membershipRequest(student);
        String path = "/api/clubs/{clubId}/membership-requests/{requestId}/approve";
        for (String token : new String[]{TestTokens.student(member), TestTokens.student(officer),
                TestTokens.academician(advisor), TestTokens.student(otherPresident), TestTokens.admin(admin)}) {
            mockMvc.perform(as(put(path, clubId, requestId), token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(as(put(path, otherClubId, requestId), TestTokens.student(otherPresident)))
                .andExpect(status().isNotFound());
        assertThat(membershipRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(MembershipRequestStatus.PENDING);
        assertThat(membershipRepository.existsByClubIdAndStudentId(clubId, student)).isFalse();

        mockMvc.perform(as(put(path, clubId, requestId), TestTokens.student(president)))
                .andExpect(status().isOk());
        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, student).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.MEMBER);
    }

    @Test
    void theSecretaryRejectsMembershipRequestsButABoardMemberCannot() throws Exception {
        UUID requestId = membershipRequest(student);
        String path = "/api/clubs/{clubId}/membership-requests/{requestId}/reject";
        mockMvc.perform(as(put(path, clubId, requestId), TestTokens.student(officer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put(path, otherClubId, requestId), TestTokens.student(otherPresident)))
                .andExpect(status().isNotFound());
        assertThat(membershipRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(MembershipRequestStatus.PENDING);

        mockMvc.perform(as(put(path, clubId, requestId), TestTokens.student(secretary)))
                .andExpect(status().isOk());
        assertThat(membershipRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(MembershipRequestStatus.REJECTED);
    }

    @Test
    void studentsCancelOnlyTheirOwnMembershipRequest() throws Exception {
        UUID requestId = membershipRequest(student);
        String path = "/api/clubs/{clubId}/membership-requests";
        mockMvc.perform(as(delete(path, clubId), TestTokens.student(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        assertThat(membershipRequestRepository.existsById(requestId)).isTrue();

        mockMvc.perform(as(delete(path, clubId), TestTokens.student(student)))
                .andExpect(status().isNoContent());
        assertThat(membershipRequestRepository.existsById(requestId)).isFalse();
    }

    @Test
    void onlyTheActingPresidentProposesPositionChanges() throws Exception {
        String body = roleChange(member, "BOARD_MEMBER");
        for (String token : new String[]{TestTokens.student(member), TestTokens.student(officer),
                TestTokens.student(vicePresident), TestTokens.student(secretary), TestTokens.academician(advisor),
                TestTokens.student(otherPresident), TestTokens.admin(admin)}) {
            mockMvc.perform(json(post("/api/clubs/{clubId}/role-change-requests", clubId), token, body))
                    .andExpect(status().isForbidden());
        }
        assertThat(approvalRequestRepository.existsByClubIdAndTypeAndSubjectUserIdAndStatusIn(
                clubId, ApprovalType.ROLE_CHANGE, member, ApprovalStatus.PENDING)).isFalse();

        mockMvc.perform(json(post("/api/clubs/{clubId}/role-change-requests", clubId), TestTokens.student(president), body))
                .andExpect(status().isCreated());
        assertThat(approvalRequestRepository.existsByClubIdAndTypeAndSubjectUserIdAndStatusIn(
                clubId, ApprovalType.ROLE_CHANGE, member, ApprovalStatus.PENDING)).isTrue();
        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, member).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.MEMBER);
    }

    @Test
    void aPresidentCannotAssignPositionsToAnotherClubsMember() throws Exception {
        mockMvc.perform(json(post("/api/clubs/{clubId}/role-change-requests", clubId), TestTokens.student(president),
                        roleChange(otherOfficer, "BOARD_MEMBER")))
                .andExpect(status().isBadRequest());
        assertThat(approvalRequestRepository.existsByClubIdAndTypeAndSubjectUserIdAndStatusIn(
                clubId, ApprovalType.ROLE_CHANGE, otherOfficer, ApprovalStatus.PENDING)).isFalse();
    }

    @Test
    void onlyThisClubsPresidentRequestsARoleRevocation() throws Exception {
        String path = "/api/clubs/{clubId}/members/{studentId}/role";
        for (String token : new String[]{TestTokens.student(member), TestTokens.student(secretary),
                TestTokens.academician(advisor), TestTokens.student(otherPresident), TestTokens.admin(admin)}) {
            mockMvc.perform(as(delete(path, clubId, officer), token))
                    .andExpect(status().isForbidden());
        }
        assertThat(approvalRequestRepository.existsByClubIdAndTypeAndSubjectUserIdAndStatusIn(
                clubId, ApprovalType.ROLE_CHANGE, officer, ApprovalStatus.PENDING)).isFalse();

        mockMvc.perform(as(delete(path, clubId, officer), TestTokens.student(president)))
                .andExpect(status().isAccepted());
        assertThat(approvalRequestRepository.existsByClubIdAndTypeAndSubjectUserIdAndStatusIn(
                clubId, ApprovalType.ROLE_CHANGE, officer, ApprovalStatus.PENDING)).isTrue();
        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, officer).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.BOARD_MEMBER);
    }

    @Test
    void onlyThisClubsAdvisorDecidesPositionChanges() throws Exception {
        UUID requestId = pendingRoleChange(member, ClubPosition.BOARD_MEMBER);
        String approve = "/api/academician/role-change-requests/{requestId}/approve";
        String reject = "/api/academician/role-change-requests/{requestId}/reject";

        mockMvc.perform(as(put(approve, requestId), TestTokens.student(president)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(put(approve, requestId), TestTokens.admin(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(put(approve, requestId), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mockMvc.perform(as(put(reject, requestId), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isForbidden());
        assertThat(approvalRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.PENDING_ADVISOR);
        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, member).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.MEMBER);

        mockMvc.perform(as(put(approve, requestId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        assertThat(approvalRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.APPROVED);
        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, member).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.BOARD_MEMBER);
    }

    @Test
    void advisorsSeeOnlyTheirOwnClubsPositionRequests() throws Exception {
        UUID requestId = pendingRoleChange(member, ClubPosition.BOARD_MEMBER);

        mockMvc.perform(as(get("/api/academician/role-change-requests"), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(requestId.toString()))));
        mockMvc.perform(as(get("/api/academician/role-change-requests"), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(requestId.toString())));

        String count = "/api/academician/clubs/{clubId}/role-change-requests/count";
        mockMvc.perform(as(get(count, clubId), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get(count, clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));
    }

    @Test
    void positionRequestHistoryIsManagementData() throws Exception {
        pendingRoleChange(member, ClubPosition.BOARD_MEMBER);
        String path = "/api/clubs/{clubId}/role-change-requests";
        for (String token : new String[]{TestTokens.student(member), TestTokens.student(student),
                TestTokens.student(otherPresident), TestTokens.academician(otherAdvisor), TestTokens.admin(admin)}) {
            mockMvc.perform(as(get(path, clubId), token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(as(get(path, clubId), TestTokens.student(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(get(path, clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyThisClubsAdvisorRemovesThePresident() throws Exception {
        String path = "/api/academician/clubs/{clubId}/president";
        mockMvc.perform(as(delete(path, clubId), TestTokens.student(president)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(delete(path, clubId), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete(path, clubId), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, president).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.PRESIDENT);

        mockMvc.perform(as(delete(path, clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, president).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.MEMBER);
    }

    @Test
    void onlyStudentsApplyToFoundAClub() throws Exception {
        String body = "{\"name\":\"Yeni Kulüp " + UUID.randomUUID() + "\",\"academicAdvisorId\":\"" + advisor + "\"}";
        mockMvc.perform(post("/api/clubs/request-creation").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/clubs/request-creation"), TestTokens.academician(otherAdvisor), body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mockMvc.perform(json(post("/api/clubs/request-creation"), TestTokens.admin(admin), body))
                .andExpect(status().isForbidden());
        assertThat(creationRequestRepository.findByStatusAndSuggestedAdvisorId(ClubCreationRequestStatus.PENDING, advisor))
                .isEmpty();
    }

    @Test
    void clubCreationIsDecidedOnlyByTheSuggestedAdvisor() throws Exception {
        ClubCreationRequest request = new ClubCreationRequest();
        request.setClubName("Kuruluş " + UUID.randomUUID());
        request.setRequestingStudentId(student);
        request.setSuggestedAdvisorId(advisor);
        UUID requestId = creationRequestRepository.save(request).getId();

        mockMvc.perform(as(get("/api/academician/club-creation-requests"), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        mockMvc.perform(as(get("/api/academician/club-creation-requests"), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(requestId.toString())));

        String approve = "/api/academician/club-creation-requests/{requestId}/approve";
        String reject = "/api/academician/club-creation-requests/{requestId}/reject";
        mockMvc.perform(as(put(approve, requestId), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mockMvc.perform(as(put(reject, requestId), TestTokens.academician(otherAdvisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put(approve, requestId), TestTokens.student(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put(approve, requestId), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        assertThat(creationRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ClubCreationRequestStatus.PENDING);

        mockMvc.perform(as(put(reject, requestId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        assertThat(creationRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ClubCreationRequestStatus.REJECTED);
    }

    @Test
    void onlyThisClubsPresidentChangesTheLogo() throws Exception {
        for (String token : new String[]{TestTokens.student(officer), TestTokens.student(vicePresident),
                TestTokens.academician(advisor), TestTokens.student(otherPresident), TestTokens.admin(admin)}) {
            mockMvc.perform(as(multipart("/api/clubs/{clubId}/logo", clubId).file(logo()), token))
                    .andExpect(status().isForbidden());
        }
        assertThat(clubRepository.findById(clubId).orElseThrow().getLogoUrl()).isNull();

        mockMvc.perform(as(multipart("/api/clubs/{clubId}/logo", clubId).file(logo()), TestTokens.student(president)))
                .andExpect(status().isOk());
        assertThat(clubRepository.findById(clubId).orElseThrow().getLogoUrl()).isNotNull();
    }

    @Test
    void membersLeaveButThePresidentCannot() throws Exception {
        String path = "/api/clubs/{clubId}/leave";
        mockMvc.perform(as(delete(path, clubId), TestTokens.student(president)))
                .andExpect(status().isConflict());
        mockMvc.perform(as(delete(path, clubId), TestTokens.student(student)))
                .andExpect(status().isNotFound());
        assertThat(membershipRepository.existsByClubIdAndStudentId(clubId, president)).isTrue();

        mockMvc.perform(as(delete(path, clubId), TestTokens.student(member)))
                .andExpect(status().isOk());
        assertThat(membershipRepository.existsByClubIdAndStudentIdAndIsActive(clubId, member, true)).isFalse();
        assertThat(membershipRepository.existsByClubIdAndStudentId(clubId, member)).isTrue();
    }

    @Test
    void personalDashboardsUseOnlyTheCallersIdentity() throws Exception {
        mockMvc.perform(get("/api/clubs/my-managed-clubs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/clubs/my-memberships"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/clubs/my-managed-clubs"), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].clubId", hasItem(clubId.toString())));
        mockMvc.perform(as(get("/api/clubs/my-managed-clubs"), TestTokens.student(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        mockMvc.perform(as(get("/api/clubs/my-membership-requests"), TestTokens.academician(advisor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminClubEndpointsAreSwitchedOffUnlessExplicitlyEnabled() throws Exception {
        String body = "{\"name\":\"Admin Kulübü\",\"academicAdvisorId\":\"" + advisor
                + "\",\"clubPresidentId\":\"" + student + "\"}";
        mockMvc.perform(json(post("/api/admin/clubs"), TestTokens.admin(admin), body))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/admin/clubs/active"), TestTokens.admin(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(delete("/api/admin/clubs/{clubId}", clubId), TestTokens.admin(admin)))
                .andExpect(status().isNotFound());
        assertThat(clubRepository.existsById(clubId)).isTrue();
        assertThat(clubRepository.findByNormalizedNameAndStatusNot(ClubNames.normalize("Admin Kulübü"), ClubStatus.CLOSED)).isEmpty();
    }

    @Test
    void membershipEndpointsRequireAToken() throws Exception {
        mockMvc.perform(post("/api/clubs/{clubId}/membership-requests", clubId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/clubs/{clubId}/membership-requests/pending", clubId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clubLookupByNameIsInternalOnly() throws Exception {
        String name = clubRepository.findById(clubId).orElseThrow().getName();
        mockMvc.perform(get("/api/clubs/internal/by-name").param("name", name))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/clubs/internal/by-name").param("name", name), TestTokens.student(president)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/clubs/internal/by-name").param("name", name), TestTokens.service("event-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(clubId.toString()));
        mockMvc.perform(as(get("/api/clubs/internal/by-name").param("name", "Olmayan Kulüp & Topluluk"),
                        TestTokens.service("event-service")))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalAccessEndpointAcceptsOnlyServiceTokens() throws Exception {
        String path = "/api/clubs/internal/{clubId}/access/{userId}";
        mockMvc.perform(get(path, clubId, president))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get(path, clubId, president), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get(path, clubId, president), TestTokens.student(president)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/clubs/internal/users/{userId}/access", president), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());

        String service = TestTokens.service("event-service");
        mockMvc.perform(as(get(path, clubId, president), service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA",
                        "MANAGE_MEMBERSHIP_REQUESTS", "PROPOSE_POSITION_CHANGE", "UPDATE_CLUB_PROFILE", "CREATE_EVENT",
                        "MANAGE_EVENT_OPERATIONS", "PROPOSE_ADVISOR_CHANGE", "APPROVE_AS_PRESIDENT", "REQUEST_CLUB_CLOSURE", "PREPARE_EVENT", "VIEW_DECISIONS",
                        "PREPARE_PROFILE_CHANGE", "PREPARE_LOGO_CHANGE", "PREPARE_ANNOUNCEMENT", "PREPARE_FINANCE", "PREPARE_SPONSORSHIP", "VIEW_FINANCE", "PREPARE_MINUTES", "PREPARE_ACTIVITY_REPORT", "PREPARE_ELECTION")));
        mockMvc.perform(as(get(path, clubId, officer), service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA",
                        "MANAGE_EVENT_OPERATIONS", "PREPARE_EVENT", "VIEW_DECISIONS", "PREPARE_ANNOUNCEMENT", "VIEW_FINANCE")));
        mockMvc.perform(as(get(path, clubId, advisor), service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA",
                        "ADVISE", "VIEW_DECISIONS", "VIEW_FINANCE")));
        mockMvc.perform(as(get(path, clubId, member), service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("VIEW_MEMBERS")));
        mockMvc.perform(as(get(path, clubId, otherPresident), service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", empty()));
        mockMvc.perform(as(get(path, clubId, otherAdvisor), service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", empty()));
    }

    private ResultActions details(String token) throws Exception {
        return mockMvc.perform(as(get("/api/clubs/{id}", clubId), token))
                .andExpect(status().isOk());
    }

    private UUID club(UUID advisorId) {
        Club club = new Club();
        club.setName("Yetki Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisorId);
        return clubRepository.save(club).getId();
    }

    private void seat(UUID club, UUID studentId, ClubPosition position) {
        membershipRepository.save(new ClubMembership(club, studentId, position));
    }

    private UUID membershipRequest(UUID studentId) {
        return membershipRequestRepository.save(new ClubMembershipRequest(clubId, studentId)).getId();
    }

    private UUID pendingRoleChange(UUID studentId, ClubPosition requested) {
        ClubPosition current = membershipRepository.findByClubIdAndStudentId(clubId, studentId).orElseThrow().getClubRole();
        return approvalRequestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.ROLE_CHANGE, president, studentId,
                current, requested, null, Instant.now()))
                .getId();
    }

    private static String roleChange(UUID studentId, String position) {
        return "{\"studentId\":\"" + studentId + "\",\"requestedRole\":\"" + position + "\"}";
    }

    private static MockMultipartFile logo() {
        return new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE, PNG);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
