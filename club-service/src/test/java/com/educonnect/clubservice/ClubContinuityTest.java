package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.clubservice.model.AdvisorChangeRequest;
import com.educonnect.clubservice.model.AdvisorChangeRequestStatus;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubMembershipRequest;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.model.MembershipRequestStatus;
import com.educonnect.clubservice.model.RoleChangeRequest;
import com.educonnect.clubservice.model.RoleChangeRequestStatus;
import com.educonnect.clubservice.repository.AdvisorChangeRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubMembershipRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.repository.RoleChangeRequestRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.service.UserDataCleanupService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubContinuityTest {

    private final UUID president = UUID.randomUUID();
    private final UUID vicePresident = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();
    private final UUID newAdvisor = UUID.randomUUID();
    private final UUID otherAcademician = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @Autowired
    private ClubMembershipRequestRepository membershipRequestRepository;

    @Autowired
    private RoleChangeRequestRepository roleChangeRequestRepository;

    @Autowired
    private AdvisorChangeRequestRepository advisorChangeRequestRepository;

    @Autowired
    private ClubAuthorizationService clubAuthorizationService;

    @Autowired
    private UserDataCleanupService userDataCleanupService;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        clubId = club("Süreklilik Kulübü " + UUID.randomUUID(), advisor);
        seat(president, ClubPosition.PRESIDENT);
        seat(vicePresident, ClubPosition.VICE_PRESIDENT);
        seat(officer, ClubPosition.BOARD_MEMBER);
        seat(member, ClubPosition.MEMBER);
        when(userClient.getAcademicianById(any())).thenAnswer(invocation -> academician(invocation.getArgument(0)));
    }

    @Test
    void removingThePresidentPromotesTheVicePresident() throws Exception {
        mockMvc.perform(as(delete("/api/academician/clubs/{clubId}/president", clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());

        assertThat(position(vicePresident)).isEqualTo(ClubPosition.PRESIDENT);
        assertThat(position(president)).isEqualTo(ClubPosition.MEMBER);
        assertThat(clubAuthorizationService.accessOf(clubId, vicePresident).actingPresident()).isTrue();
    }

    @Test
    void withoutAVicePresidentOnlyTheAdvisorAppointsAPresidentFromActiveMembers() throws Exception {
        membershipRepository.findByClubIdAndStudentId(clubId, vicePresident).ifPresent(membershipRepository::delete);
        mockMvc.perform(as(delete("/api/academician/clubs/{clubId}/president", clubId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        assertThat(membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true)).isEmpty();

        String appoint = "/api/academician/clubs/{clubId}/president";
        mockMvc.perform(json(put(appoint, clubId), TestTokens.academician(otherAcademician), studentBody(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put(appoint, clubId), TestTokens.academician(advisor), studentBody(student)))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(put(appoint, clubId), TestTokens.academician(advisor), studentBody(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(member.toString()));
        assertThat(position(member)).isEqualTo(ClubPosition.PRESIDENT);
        mockMvc.perform(json(put(appoint, clubId), TestTokens.academician(advisor), studentBody(officer)))
                .andExpect(status().isConflict());
    }

    @Test
    void thePresidentProposesANewAdvisorWhoAcceptsIt() throws Exception {
        String propose = "/api/clubs/{clubId}/advisor-change-requests";
        mockMvc.perform(json(post(propose, clubId), TestTokens.student(officer), advisorBody(newAdvisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(propose, clubId), TestTokens.student(president), advisorBody(advisor)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(propose, clubId), TestTokens.student(president), advisorBody(newAdvisor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.previousAdvisorId").value(advisor.toString()));
        mockMvc.perform(json(post(propose, clubId), TestTokens.student(president), advisorBody(otherAcademician)))
                .andExpect(status().isConflict());

        String requestId = advisorChangeRequestRepository.findByClubIdOrderByCreatedAtDesc(clubId).getFirst().getId().toString();
        mockMvc.perform(as(get("/api/academician/advisor-change-requests"), TestTokens.academician(newAdvisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(put("/api/academician/advisor-change-requests/{id}/accept", requestId),
                        TestTokens.academician(otherAcademician)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(put("/api/academician/advisor-change-requests/{id}/accept", requestId),
                        TestTokens.academician(newAdvisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Club club = clubRepository.findById(clubId).orElseThrow();
        assertThat(club.getAcademicAdvisorId()).isEqualTo(newAdvisor);
        assertThat(clubAuthorizationService.accessOf(clubId, advisor).advisor()).isFalse();
        assertThat(clubAuthorizationService.accessOf(clubId, newAdvisor).advisor()).isTrue();
    }

    @Test
    void aResigningAdvisorLeavesTheClubAwaitingANewOne() throws Exception {
        mockMvc.perform(as(post("/api/academician/clubs/{clubId}/advisor/resign", clubId), TestTokens.academician(advisor)))
                .andExpect(status().isNoContent());
        Club club = clubRepository.findById(clubId).orElseThrow();
        assertThat(club.getStatus()).isEqualTo(ClubStatus.AWAITING_ADVISOR);
        assertThat(club.getAcademicAdvisorId()).isNull();
        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_ADVISOR"));

        mockMvc.perform(json(post("/api/clubs/{clubId}/advisor-change-requests", clubId), TestTokens.student(president),
                        advisorBody(newAdvisor)))
                .andExpect(status().isCreated());
        String requestId = advisorChangeRequestRepository.findByClubIdOrderByCreatedAtDesc(clubId).getFirst().getId().toString();
        mockMvc.perform(as(put("/api/academician/advisor-change-requests/{id}/accept", requestId),
                        TestTokens.academician(newAdvisor)))
                .andExpect(status().isOk());
        club = clubRepository.findById(clubId).orElseThrow();
        assertThat(club.getStatus()).isEqualTo(ClubStatus.ACTIVE);
        assertThat(club.getAcademicAdvisorId()).isEqualTo(newAdvisor);
    }

    @Test
    void theAdvisorClosesTheClubAndItBecomesReadOnly() throws Exception {
        membershipRequestRepository.save(new ClubMembershipRequest(clubId, student));
        UUID roleChange = roleChangeRequestRepository.save(new RoleChangeRequest(clubId, member, ClubPosition.MEMBER,
                ClubPosition.BOARD_MEMBER, president)).getId();
        String close = "/api/academician/clubs/{clubId}/close";

        mockMvc.perform(json(post(close, clubId), TestTokens.academician(otherAcademician), reasonBody("Kapat")))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(close, clubId), TestTokens.academician(advisor), "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(close, clubId), TestTokens.academician(advisor), reasonBody("Genel kurul kararı")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(json(post(close, clubId), TestTokens.academician(advisor), reasonBody("Tekrar")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closureReason").value("Genel kurul kararı"));
        mockMvc.perform(get("/api/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + clubId + "')]").isEmpty());
        mockMvc.perform(json(post("/api/clubs/{clubId}/membership-requests", clubId), TestTokens.student(UUID.randomUUID()), "{}"))
                .andExpect(status().isConflict());
        mockMvc.perform(as(delete("/api/clubs/{clubId}/leave", clubId), TestTokens.student(member)))
                .andExpect(status().isConflict());
        mockMvc.perform(json(post("/api/clubs/{clubId}/advisor-change-requests", clubId), TestTokens.student(president),
                        advisorBody(newAdvisor)))
                .andExpect(status().isForbidden());

        assertThat(membershipRequestRepository.findByClubIdAndStatus(clubId, MembershipRequestStatus.PENDING)).isEmpty();
        assertThat(roleChangeRequestRepository.findById(roleChange).orElseThrow().getStatus())
                .isEqualTo(RoleChangeRequestStatus.REJECTED);
        assertThat(membershipRepository.findByClubId(clubId)).hasSize(4);
        assertThat(clubAuthorizationService.activeManagementPositionOf(president)).isEmpty();
        assertThat(clubAuthorizationService.accessesOf(president)).isEmpty();
    }

    @Test
    void clubNamesAreUniqueIgnoringCaseAndTurkishCharactersButClosedNamesCanBeReused() throws Exception {
        UUID existing = club("Yapay Zeka Kulübü " + clubId, advisor);
        mockMvc.perform(json(post("/api/clubs/request-creation"), TestTokens.student(student),
                        creationBody("YAPAY ZEKÂ  KULUBU " + clubId, newAdvisor)))
                .andExpect(status().isConflict());

        Club closed = clubRepository.findById(existing).orElseThrow();
        closed.close(advisor, "Kapandı", Instant.now());
        clubRepository.save(closed);
        mockMvc.perform(json(post("/api/clubs/request-creation"), TestTokens.student(student),
                        creationBody("YAPAY ZEKÂ  KULUBU " + clubId, newAdvisor)))
                .andExpect(status().isOk());
    }

    @Test
    void deletingThePresidentOrTheAdvisorKeepsTheClubRunning() {
        userDataCleanupService.deleteUserData(president);
        assertThat(position(vicePresident)).isEqualTo(ClubPosition.PRESIDENT);

        advisorChangeRequestRepository.save(new AdvisorChangeRequest(
                clubId, advisor, null, vicePresident, null));
        userDataCleanupService.deleteUserData(advisor);
        Club club = clubRepository.findById(clubId).orElseThrow();
        assertThat(club.getStatus()).isEqualTo(ClubStatus.AWAITING_ADVISOR);
        assertThat(club.getAcademicAdvisorId()).isNull();
        assertThat(advisorChangeRequestRepository.existsByClubIdAndStatus(clubId, AdvisorChangeRequestStatus.PENDING)).isFalse();
    }

    private ClubPosition position(UUID studentId) {
        return membershipRepository.findByClubIdAndStudentId(clubId, studentId).orElseThrow().getClubRole();
    }

    private UUID club(String name, UUID advisorId) {
        Club club = new Club();
        club.setName(name);
        club.setAcademicAdvisorId(advisorId);
        return clubRepository.save(club).getId();
    }

    private void seat(UUID studentId, ClubPosition position) {
        membershipRepository.save(new ClubMembership(clubId, studentId, position));
    }

    private static AcademicianSummary academician(UUID id) {
        AcademicianSummary summary = new AcademicianSummary();
        summary.setId(id);
        summary.setFirstName("Test");
        summary.setLastName("Akademisyen");
        summary.setRole("ACADEMICIAN");
        return summary;
    }

    private static String studentBody(UUID studentId) {
        return "{\"studentId\":\"" + studentId + "\"}";
    }

    private static String advisorBody(UUID advisorId) {
        return "{\"proposedAdvisorId\":\"" + advisorId + "\"}";
    }

    private static String reasonBody(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    private static String creationBody(String name, UUID advisorId) {
        return "{\"name\":\"" + name + "\",\"about\":\"Test\",\"academicAdvisorId\":\"" + advisorId + "\"}";
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
