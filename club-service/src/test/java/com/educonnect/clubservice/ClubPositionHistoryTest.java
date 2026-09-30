package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubPositionHistoryTest {

    private final UUID president = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
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
        club.setName("Geçmiş Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        membershipRepository.save(new ClubMembership(clubId, president, ClubPosition.PRESIDENT));
        membershipRepository.save(new ClubMembership(clubId, member, ClubPosition.MEMBER));
    }

    @Test
    void everyPositionChangeIsKeptAsATermAndClosingTheClubEndsTheOpenOnes() throws Exception {
        assign(ClubPosition.BOARD_MEMBER);
        assign(ClubPosition.GENERAL_SECRETARY);
        mockMvc.perform(as(delete("/api/clubs/{clubId}/leave", clubId), TestTokens.student(member)))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(as(get("/api/clubs/my-positions"), TestTokens.student(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].position", contains(ClubPosition.GENERAL_SECRETARY.apiName(), ClubPosition.BOARD_MEMBER.apiName())))
                .andExpect(jsonPath("$[*].endReason", contains("LEFT_CLUB", "CHANGED")));
        mockMvc.perform(as(get("/api/clubs/{clubId}/position-history", clubId), TestTokens.student(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/clubs/{clubId}/position-history", clubId), TestTokens.student(president)))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.positionName == 'Kulüp Başkanı')].endReason").value(contains(nullValue())));

        mockMvc.perform(json(post("/api/academician/clubs/{clubId}/close", clubId), TestTokens.academician(advisor),
                        "{\"reason\":\"Dönem sonu\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/clubs/{clubId}/position-history", clubId), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[?(@.positionName == 'Kulüp Başkanı')].endReason").value(contains("CLUB_CLOSED")));
        mockMvc.perform(as(get("/api/clubs/{clubId}/past-presidents", clubId), TestTokens.student(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentId").value(president.toString()));
    }

    private void assign(ClubPosition position) throws Exception {
        String body = "{\"studentId\":\"" + member + "\",\"requestedRole\":\"" + position.name() + "\"}";
        mockMvc.perform(json(post("/api/clubs/{clubId}/role-change-requests", clubId), TestTokens.student(president), body))
                .andExpect(status().is2xxSuccessful());
        UUID requestId = approvalRequestRepository.findByClubIdAndTypeOrderByCreatedAtDesc(clubId, ApprovalType.ROLE_CHANGE)
                .getFirst().getId();
        mockMvc.perform(as(put("/api/academician/role-change-requests/{id}/approve", requestId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
