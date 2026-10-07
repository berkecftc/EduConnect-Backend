package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubGovernanceTest {

    private final UUID president = UUID.randomUUID();
    private final UUID vicePresident = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @Autowired
    private ClubApprovalRequestRepository approvalRequestRepository;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Yönetişim Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(vicePresident, ClubPosition.VICE_PRESIDENT);
        seat(officer, ClubPosition.BOARD_MEMBER);
        seat(member, ClubPosition.MEMBER);
        given(userClient.getUsersByIds(any())).willAnswer(call -> call.<Collection<UUID>>getArgument(0).stream()
                .map(ClubGovernanceTest::summary)
                .toList());
    }

    @Test
    void anOfficersResignationGoesThroughThePresidentAndThenTheAdvisor() throws Exception {
        String requestId = created(mockMvc.perform(json(post("/api/clubs/{clubId}/resignations", clubId),
                        TestTokens.student(officer), "{\"note\":\"Ders yoğunluğu\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RESIGNATION"))
                .andExpect(jsonPath("$.status").value("PENDING_PRESIDENT")));

        mockMvc.perform(as(get("/api/clubs/approvals/inbox"), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItems(requestId)))
                .andExpect(jsonPath("$[?(@.id == '" + requestId + "')].preparedByName").value(hasItems(nameOf(officer))))
                .andExpect(jsonPath("$[?(@.id == '" + requestId + "')].subjectUserName").value(hasItems(nameOf(officer))));
        mockMvc.perform(as(post(approve(requestId), clubId), TestTokens.academician(advisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post(approve(requestId), clubId), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_ADVISOR"))
                .andExpect(jsonPath("$.presidentDecidedBy").value(president.toString()))
                .andExpect(jsonPath("$.presidentDecidedByName").value(nameOf(president)));
        assertThat(position(officer)).isEqualTo(ClubPosition.BOARD_MEMBER);

        mockMvc.perform(as(get("/api/clubs/approvals/inbox"), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[*].id", hasItems(requestId)));
        mockMvc.perform(as(post(approve(requestId), clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedByName").value(nameOf(advisor)));
        assertThat(position(officer)).isEqualTo(ClubPosition.MEMBER);

        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-log", clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("SUBMITTED", "PRESIDENT_APPROVED", "APPROVED")));
        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-log", clubId), TestTokens.student(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void myAccessTellsTheCallerWhatTheyCanDoInEachClub() throws Exception {
        mockMvc.perform(as(get("/api/clubs/{clubId}/my-access", clubId), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("PRESIDENT"))
                .andExpect(jsonPath("$.actingPresident").value(true))
                .andExpect(jsonPath("$.permissions", hasItems("APPROVE_AS_PRESIDENT", "MANAGE_MEMBERSHIP_REQUESTS")));
        mockMvc.perform(as(get("/api/clubs/{clubId}/my-access", clubId), TestTokens.student(member)))
                .andExpect(jsonPath("$.member").value(true))
                .andExpect(jsonPath("$.position").value("MEMBER"))
                .andExpect(jsonPath("$.actingPresident").value(false));
        mockMvc.perform(as(get("/api/clubs/{clubId}/my-access", clubId), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$.advisor").value(true))
                .andExpect(jsonPath("$.member").value(false));
        mockMvc.perform(get("/api/clubs/{clubId}/my-access", clubId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(as(get("/api/clubs/my-access"), TestTokens.student(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.clubId == '" + clubId + "')].position").value(hasItems("BOARD_MEMBER")));
        mockMvc.perform(as(get("/api/clubs/my-access"), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[?(@.clubId == '" + clubId + "')].advisor").value(hasItems(true)));
        mockMvc.perform(as(get("/api/clubs/my-access"), TestTokens.student(member)))
                .andExpect(jsonPath("$[?(@.clubId == '" + clubId + "')]").isEmpty());
        mockMvc.perform(get("/api/clubs/my-access"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(json(post("/api/clubs/{clubId}/resignations", clubId), TestTokens.student(president), "{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(as(post(approve(approvalRequestRepository.findAll().stream()
                        .filter(request -> request.getClubId().equals(clubId))
                        .findFirst().orElseThrow().getId().toString()), clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/clubs/{clubId}/my-access", clubId), TestTokens.student(vicePresident)))
                .andExpect(jsonPath("$.actingPresident").value(true));
    }

    @Test
    void thePresidentsResignationGoesStraightToTheAdvisorAndTheVicePresidentTakesOver() throws Exception {
        String requestId = created(mockMvc.perform(json(post("/api/clubs/{clubId}/resignations", clubId),
                        TestTokens.student(president), "{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_ADVISOR")));
        mockMvc.perform(json(post("/api/clubs/{clubId}/resignations", clubId), TestTokens.student(president), "{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(as(post(approve(requestId), clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        assertThat(position(president)).isEqualTo(ClubPosition.MEMBER);
        assertThat(position(vicePresident)).isEqualTo(ClubPosition.PRESIDENT);
    }

    @Test
    void onlyOfficersCanResign() throws Exception {
        mockMvc.perform(json(post("/api/clubs/{clubId}/resignations", clubId), TestTokens.student(member), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thePresidentRequestsClosureAndTheAdvisorDecidesWithAReason() throws Exception {
        UUID pendingRoleChange = approvalRequestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.ROLE_CHANGE,
                president, member, ClubPosition.MEMBER, ClubPosition.BOARD_MEMBER, null, Instant.now())).getId();

        mockMvc.perform(json(post("/api/clubs/{clubId}/closure-requests", clubId), TestTokens.student(officer),
                        "{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        String first = created(mockMvc.perform(json(post("/api/clubs/{clubId}/closure-requests", clubId),
                        TestTokens.student(president), "{\"reason\":\"Genel kurul kararı\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_ADVISOR")));

        mockMvc.perform(json(post(reject(first), clubId), TestTokens.academician(advisor), "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(reject(first), clubId), TestTokens.academician(advisor), "{\"reason\":\"Erken\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Erken"));
        assertThat(clubRepository.findById(clubId).orElseThrow().getStatus()).isEqualTo(ClubStatus.ACTIVE);

        String second = created(mockMvc.perform(json(post("/api/clubs/{clubId}/closure-requests", clubId),
                        TestTokens.student(president), "{\"reason\":\"Genel kurul kararı\"}"))
                .andExpect(status().isCreated()));
        mockMvc.perform(as(post(approve(second), clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(clubRepository.findById(clubId).orElseThrow().getStatus()).isEqualTo(ClubStatus.CLOSED);
        assertThat(approvalRequestRepository.findById(pendingRoleChange).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.REJECTED);
        mockMvc.perform(as(post(approve(second), clubId), TestTokens.academician(advisor)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyThePreparerOrThePresidentCanWithdraw() throws Exception {
        String requestId = created(mockMvc.perform(json(post("/api/clubs/{clubId}/resignations", clubId),
                        TestTokens.student(officer), "{}"))
                .andExpect(status().isCreated()));

        mockMvc.perform(as(post(withdraw(requestId), clubId), TestTokens.student(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post(withdraw(requestId), clubId), TestTokens.student(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mockMvc.perform(as(get("/api/clubs/{clubId}/approvals", clubId), TestTokens.student(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/clubs/{clubId}/approvals", clubId), TestTokens.student(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"));
    }

    @Test
    void legacyRoleChangeDecisionsGoThroughTheEngine() throws Exception {
        UUID requestId = approvalRequestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.ROLE_CHANGE,
                president, member, ClubPosition.MEMBER, ClubPosition.TREASURER, null, Instant.now())).getId();

        mockMvc.perform(as(put("/api/academician/role-change-requests/{id}/approve", requestId), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.requestedRole").exists());
        assertThat(position(member)).isEqualTo(ClubPosition.TREASURER);
        mockMvc.perform(as(put("/api/academician/role-change-requests/{id}/approve", requestId), TestTokens.academician(advisor)))
                .andExpect(status().isConflict());
        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-log", clubId), TestTokens.student(president)))
                .andExpect(jsonPath("$[*].action", hasItems("APPROVED")));
    }

    private ClubPosition position(UUID studentId) {
        return membershipRepository.findByClubIdAndStudentId(clubId, studentId).orElseThrow().getClubRole();
    }

    private static UserSummary summary(UUID id) {
        UserSummary user = new UserSummary();
        user.setId(id);
        user.setFirstName("Kişi");
        user.setLastName(id.toString().substring(0, 8));
        return user;
    }

    private static String nameOf(UUID id) {
        return "Kişi " + id.toString().substring(0, 8);
    }

    private void seat(UUID studentId, ClubPosition position) {
        membershipRepository.save(new ClubMembership(clubId, studentId, position));
    }

    private static String approve(String requestId) {
        return "/api/clubs/{clubId}/approvals/" + requestId + "/approve";
    }

    private static String reject(String requestId) {
        return "/api/clubs/{clubId}/approvals/" + requestId + "/reject";
    }

    private static String withdraw(String requestId) {
        return "/api/clubs/{clubId}/approvals/" + requestId + "/withdraw";
    }

    private static String created(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"([0-9a-f-]{36})\".*", "$1");
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
