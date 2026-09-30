package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubMembershipRequest;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubMembershipRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubPositionsTest {

    private final UUID president = UUID.randomUUID();
    private final UUID auditor = UUID.randomUUID();
    private final UUID eventCoordinator = UUID.randomUUID();
    private final UUID communicationsOfficer = UUID.randomUUID();
    private final UUID membershipOfficer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID applicant = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

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
    private ClubAuthorizationService clubAuthorizationService;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Görevler Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(auditor, ClubPosition.AUDITOR);
        seat(eventCoordinator, ClubPosition.EVENT_COORDINATOR);
        seat(communicationsOfficer, ClubPosition.COMMUNICATIONS_OFFICER);
        seat(membershipOfficer, ClubPosition.MEMBERSHIP_OFFICER);
        seat(member, ClubPosition.MEMBER);
    }

    @Test
    void thePresidentAssignsNewPositionsThroughTheAdvisor() throws Exception {
        String body = "{\"studentId\":\"" + member + "\",\"requestedRole\":\"AUDITOR\"}";
        mockMvc.perform(json(post("/api/clubs/{clubId}/role-change-requests", clubId), TestTokens.student(president), body))
                .andExpect(status().is2xxSuccessful());
        UUID requestId = approvalRequestRepository.findByClubIdAndTypeOrderByCreatedAtDesc(clubId, ApprovalType.ROLE_CHANGE)
                .getFirst().getId();
        mockMvc.perform(as(put("/api/academician/role-change-requests/{id}/approve", requestId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        assertThat(position(member)).isEqualTo(ClubPosition.AUDITOR);
    }

    @Test
    void theAuditBoardHasAtMostThreeMembers() throws Exception {
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        seat(second, ClubPosition.AUDITOR);
        approvalRequestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.ROLE_CHANGE, president, third,
                ClubPosition.MEMBER, ClubPosition.AUDITOR, null, Instant.now()));
        seat(third, ClubPosition.MEMBER);

        String body = "{\"studentId\":\"" + member + "\",\"requestedRole\":\"AUDITOR\"}";
        mockMvc.perform(json(post("/api/clubs/{clubId}/role-change-requests", clubId), TestTokens.student(president), body))
                .andExpect(status().isConflict());
    }

    @Test
    void eachPositionGetsOnlyItsOwnPermissions() {
        assertThat(clubAuthorizationService.accessOf(clubId, auditor).permissions()).containsExactlyInAnyOrder(
                ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA, ClubPermission.VIEW_DECISIONS);
        assertThat(clubAuthorizationService.accessOf(clubId, eventCoordinator).permissions()).containsExactlyInAnyOrder(
                ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA, ClubPermission.MANAGE_EVENT_OPERATIONS,
                ClubPermission.PREPARE_EVENT);
        assertThat(clubAuthorizationService.accessOf(clubId, communicationsOfficer).permissions()).containsExactlyInAnyOrder(
                ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA,
                ClubPermission.PREPARE_PROFILE_CHANGE, ClubPermission.PREPARE_LOGO_CHANGE, ClubPermission.PREPARE_ANNOUNCEMENT);
        assertThat(clubAuthorizationService.accessOf(clubId, membershipOfficer).permissions()).containsExactlyInAnyOrder(
                ClubPermission.VIEW_MEMBERS, ClubPermission.VIEW_MANAGEMENT_DATA, ClubPermission.REVIEW_MEMBERSHIP_REQUESTS);
    }

    @Test
    void theAuditorReadsTheDecisionHistoryButCoordinatorsDoNot() throws Exception {
        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-log", clubId), TestTokens.student(auditor)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/clubs/{clubId}/approvals", clubId), TestTokens.student(auditor)))
                .andExpect(status().isOk());
        for (UUID coordinator : new UUID[]{eventCoordinator, communicationsOfficer, membershipOfficer}) {
            mockMvc.perform(as(get("/api/clubs/{clubId}/decision-log", clubId), TestTokens.student(coordinator)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(as(get("/api/clubs/{clubId}/membership-requests/pending", clubId), TestTokens.student(auditor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theMembershipOfficerRecommendsAndThePresidentDecides() throws Exception {
        UUID requestId = membershipRequestRepository.save(new ClubMembershipRequest(clubId, applicant)).getId();
        String recommendation = "/api/clubs/{clubId}/membership-requests/{requestId}/recommendation";

        mockMvc.perform(as(get("/api/clubs/{clubId}/membership-requests/pending", clubId), TestTokens.student(membershipOfficer)))
                .andExpect(status().isOk());
        mockMvc.perform(json(put(recommendation, clubId, requestId), TestTokens.student(member), "{\"recommendation\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put(recommendation, clubId, requestId), TestTokens.student(membershipOfficer), "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(put(recommendation, clubId, requestId), TestTokens.student(membershipOfficer),
                        "{\"recommendation\":\"APPROVE\",\"note\":\"Aktif gönüllü\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").value("APPROVE"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(as(put("/api/clubs/{clubId}/membership-requests/{requestId}/approve", clubId, requestId),
                        TestTokens.student(membershipOfficer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(get("/api/clubs/{clubId}/membership-requests/pending", clubId), TestTokens.student(president)))
                .andExpect(jsonPath("$[0].recommendationNote").value("Aktif gönüllü"));
        mockMvc.perform(as(put("/api/clubs/{clubId}/membership-requests/{requestId}/approve", clubId, requestId),
                        TestTokens.student(president)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-log", clubId), TestTokens.student(president)))
                .andExpect(jsonPath("$[*].action", hasItem("MEMBERSHIP_REVIEWED")))
                .andExpect(jsonPath("$[*].action", hasItem("MEMBERSHIP_APPROVED")));
    }

    private ClubPosition position(UUID studentId) {
        return membershipRepository.findByClubIdAndStudentId(clubId, studentId).orElseThrow().getClubRole();
    }

    private void seat(UUID studentId, ClubPosition position) {
        membershipRepository.save(new ClubMembership(clubId, studentId, position));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
