package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubMembershipRequest;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.MembershipEndReason;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubMembershipRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.service.MembershipRenewalService;
import com.educonnect.clubservice.service.MembershipTerms;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubMembershipLifecycleTest {

    private final UUID president = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
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
    private MembershipRenewalService renewalService;

    @Autowired
    private MembershipTerms membershipTerms;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Yaşam Döngüsü Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(officer, ClubPosition.BOARD_MEMBER);
        seat(member, ClubPosition.MEMBER);
        seat(other, ClubPosition.MEMBER);
    }

    @Test
    void leavingKeepsTheMembershipAsHistoryAndRejoiningReopensIt() throws Exception {
        UUID membershipId = membership(member).getId();
        mockMvc.perform(as(delete("/api/clubs/{clubId}/leave", clubId), TestTokens.student(member)))
                .andExpect(status().is2xxSuccessful());

        ClubMembership left = membership(member);
        assertThat(left.isActive()).isFalse();
        assertThat(left.getEndReason()).isEqualTo(MembershipEndReason.LEFT);
        mockMvc.perform(as(get("/api/clubs/my-memberships"), TestTokens.student(member)))
                .andExpect(jsonPath("$[?(@.clubId == '" + clubId + "')]").isEmpty());
        mockMvc.perform(as(get("/api/clubs/my-memberships/history"), TestTokens.student(member)))
                .andExpect(jsonPath("$[?(@.clubId == '" + clubId + "')].endReason").value(contains("LEFT")));
        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(jsonPath("$.memberCount").value(3));

        UUID requestId = membershipRequestRepository.save(new ClubMembershipRequest(clubId, member)).getId();
        mockMvc.perform(as(put("/api/clubs/{clubId}/membership-requests/{requestId}/approve", clubId, requestId),
                        TestTokens.student(president)))
                .andExpect(status().isOk());
        ClubMembership rejoined = membership(member);
        assertThat(rejoined.getId()).isEqualTo(membershipId);
        assertThat(rejoined.isActive()).isTrue();
        assertThat(rejoined.getEndReason()).isNull();
        assertThat(rejoined.getValidUntil()).isEqualTo(membershipTerms.currentValidUntil());
    }

    @Test
    void aBoardMemberWhoLeavesGivesUpThePositionToo() throws Exception {
        mockMvc.perform(as(delete("/api/clubs/{clubId}/leave", clubId), TestTokens.student(officer)))
                .andExpect(status().is2xxSuccessful());
        ClubMembership left = membership(officer);
        assertThat(left.getClubRole()).isEqualTo(ClubPosition.MEMBER);
        assertThat(left.getTermEndDate()).isNotNull();
        assertThat(left.isActive()).isFalse();
    }

    @Test
    void expulsionNeedsTheAdvisorAndTheMemberCanDefendThemselves() throws Exception {
        String path = "/api/clubs/{clubId}/expulsion-requests";
        mockMvc.perform(json(post(path, clubId), TestTokens.student(officer), body(member, "Kurallara aykırı")))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(path, clubId), TestTokens.student(president), "{\"studentId\":\"" + member + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(path, clubId), TestTokens.student(president), body(president, "x")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(path, clubId), TestTokens.student(president), body(member, "Kurallara aykırı davranış")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MEMBER_EXPULSION"))
                .andExpect(jsonPath("$.status").value("PENDING_ADVISOR"));
        UUID requestId = approvalRequestRepository.findByClubIdAndTypeOrderByCreatedAtDesc(clubId, ApprovalType.MEMBER_EXPULSION)
                .getFirst().getId();

        String defence = "/api/clubs/{clubId}/approvals/{requestId}/defence";
        mockMvc.perform(json(post(defence, clubId, requestId), TestTokens.student(other), "{\"note\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(defence, clubId, requestId), TestTokens.student(member), "{\"note\":\"Yanlış anlaşılma oldu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseNote").value("Yanlış anlaşılma oldu"));
        mockMvc.perform(as(get("/api/clubs/approvals/inbox"), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[?(@.type == 'MEMBER_EXPULSION')].responseNote").value(contains("Yanlış anlaşılma oldu")));

        mockMvc.perform(as(post("/api/clubs/{clubId}/approvals/{requestId}/approve", clubId, requestId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        ClubMembership expelled = membership(member);
        assertThat(expelled.isActive()).isFalse();
        assertThat(expelled.getEndReason()).isEqualTo(MembershipEndReason.EXPELLED);
        mockMvc.perform(json(post(defence, clubId, requestId), TestTokens.student(member), "{\"note\":\"geç\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void ordinaryMembershipsExpireAtTheYearEndAndCanBeRenewedWithOneClick() throws Exception {
        ClubMembership due = membership(member);
        due.setValidUntil(LocalDate.now().minusDays(1));
        membershipRepository.save(due);
        ClubMembership board = membership(officer);
        board.setValidUntil(LocalDate.now().minusDays(1));
        membershipRepository.save(board);

        assertThat(renewalService.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(membership(member).isActive()).isFalse();
        assertThat(membership(member).getEndReason()).isEqualTo(MembershipEndReason.EXPIRED);
        assertThat(membership(officer).isActive()).isTrue();

        mockMvc.perform(as(post("/api/clubs/{clubId}/membership/renew", clubId), TestTokens.student(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(membership(member).getValidUntil()).isEqualTo(membershipTerms.currentValidUntil());

        ClubMembership farAway = membership(other);
        farAway.setValidUntil(LocalDate.now().plusYears(3));
        membershipRepository.save(farAway);
        mockMvc.perform(as(post("/api/clubs/{clubId}/membership/renew", clubId), TestTokens.student(other)))
                .andExpect(status().isConflict());
    }

    @Test
    void aMemberWhoLeftMustApplyAgainInsteadOfRenewing() throws Exception {
        mockMvc.perform(as(delete("/api/clubs/{clubId}/leave", clubId), TestTokens.student(member)))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(as(post("/api/clubs/{clubId}/membership/renew", clubId), TestTokens.student(member)))
                .andExpect(status().isConflict());
    }

    private ClubMembership membership(UUID studentId) {
        return membershipRepository.findByClubIdAndStudentId(clubId, studentId).orElseThrow();
    }

    private void seat(UUID studentId, ClubPosition position) {
        ClubMembership membership = new ClubMembership(clubId, studentId, position);
        membership.setValidUntil(membershipTerms.currentValidUntil());
        membershipRepository.save(membership);
    }

    private static String body(UUID studentId, String reason) {
        return "{\"studentId\":\"" + studentId + "\",\"reason\":\"" + reason + "\"}";
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
