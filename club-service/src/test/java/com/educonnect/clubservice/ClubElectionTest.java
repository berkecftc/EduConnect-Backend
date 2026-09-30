package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubPositionTermRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.common.test.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubElectionTest {

    private final UUID president = UUID.randomUUID();
    private final UUID secretary = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bora = UUID.randomUUID();
    private final UUID cem = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @Autowired
    private ClubPositionTermRepository termRepository;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Seçim Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(secretary, ClubPosition.GENERAL_SECRETARY);
        seat(alice, ClubPosition.MEMBER);
        seat(bora, ClubPosition.MEMBER);
        seat(cem, ClubPosition.MEMBER);
    }

    @Test
    void theGeneralAssemblyElectsTheNewBoardAndTheAdvisorApprovesTheHandover() throws Exception {
        String path = "/api/clubs/{clubId}/elections";
        String seats = "{\"boardSeats\":2,\"auditSeats\":1}";
        mockMvc.perform(json(post(path, clubId), TestTokens.student(alice), seats)).andExpect(status().isForbidden());
        String opened = mockMvc.perform(json(post(path, clubId), TestTokens.student(secretary), seats))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANDIDACY"))
                .andReturn().getResponse().getContentAsString();
        String electionId = JsonPath.read(opened, "$.id");
        mockMvc.perform(json(post(path, clubId), TestTokens.student(secretary), seats)).andExpect(status().isConflict());

        String candidacy = path + "/{electionId}/candidacy";
        nominate(candidacy, electionId, alice, "PRESIDENT").andExpect(status().isOk());
        nominate(candidacy, electionId, alice, "BOARD").andExpect(status().isConflict());
        nominate(candidacy, electionId, bora, "BOARD").andExpect(status().isOk());
        nominate(candidacy, electionId, cem, "BOARD").andExpect(status().isOk());
        nominate(candidacy, electionId, president, "BOARD").andExpect(status().isOk());
        nominate(candidacy, electionId, outsider, "AUDIT").andExpect(status().isForbidden());

        String votes = path + "/{electionId}/votes";
        vote(votes, electionId, alice, "PRESIDENT", candidate(electionId, alice)).andExpect(status().isConflict());
        mockMvc.perform(as(post(path + "/{electionId}/start-voting", clubId, electionId), TestTokens.student(secretary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOTING"));

        String aliceId = candidate(electionId, alice);
        String boraId = candidate(electionId, bora);
        String cemId = candidate(electionId, cem);
        String presidentId = candidate(electionId, president);
        vote(votes, electionId, bora, "BOARD", boraId, cemId, presidentId).andExpect(status().isBadRequest());
        vote(votes, electionId, bora, "BOARD", aliceId).andExpect(status().isBadRequest());
        for (UUID voter : List.of(alice, bora, cem, secretary)) {
            vote(votes, electionId, voter, "PRESIDENT", aliceId).andExpect(status().isOk());
            vote(votes, electionId, voter, "BOARD", boraId, cemId).andExpect(status().isOk());
        }
        vote(votes, electionId, president, "BOARD", presidentId, boraId).andExpect(status().isOk());
        vote(votes, electionId, alice, "PRESIDENT", aliceId).andExpect(status().isConflict());
        mockMvc.perform(as(get(path + "/{electionId}", clubId, electionId), TestTokens.student(bora)))
                .andExpect(jsonPath("$.candidates[*].votes", contains(nullValue(), nullValue(), nullValue(), nullValue())))
                .andExpect(jsonPath("$.myBallots", contains("PRESIDENT", "BOARD")));

        String closed = mockMvc.perform(as(post(path + "/{electionId}/close", clubId, electionId), TestTokens.student(secretary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CLUB_ELECTION"))
                .andExpect(jsonPath("$.status").value("PENDING_PRESIDENT"))
                .andExpect(jsonPath("$.election.voters").value(5))
                .andExpect(jsonPath("$.election.eligibleVoters").value(5))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(closed, "$.id");
        assertThat(JsonPath.<List<String>>read(closed, "$.election.candidates[?(@.elected == true)].studentId"))
                .containsExactlyInAnyOrder(alice.toString(), bora.toString(), cem.toString());

        approve(requestId, TestTokens.student(president)).andExpect(jsonPath("$.status").value("PENDING_ADVISOR"));
        approve(requestId, TestTokens.academician(advisor)).andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(position(alice)).isEqualTo(ClubPosition.PRESIDENT);
        assertThat(position(bora)).isEqualTo(ClubPosition.BOARD_MEMBER);
        assertThat(position(cem)).isEqualTo(ClubPosition.BOARD_MEMBER);
        assertThat(position(president)).isEqualTo(ClubPosition.MEMBER);
        assertThat(position(secretary)).isEqualTo(ClubPosition.MEMBER);
        assertThat(termRepository.findByStudentIdOrderByStartedAtDesc(president).getFirst().getEndReason())
                .hasToString("HANDOVER");
        mockMvc.perform(as(get(path + "/{electionId}", clubId, electionId), TestTokens.student(alice)))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private String candidate(String electionId, UUID studentId) throws Exception {
        String election = mockMvc.perform(as(get("/api/clubs/{clubId}/elections/{electionId}", clubId, electionId),
                        TestTokens.student(president)))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(election, "$.candidates[?(@.studentId == '" + studentId + "')].id");
        return ids.getFirst();
    }

    private ResultActions nominate(String path, String electionId, UUID studentId, String ballot) throws Exception {
        return mockMvc.perform(json(post(path, clubId, electionId), TestTokens.student(studentId), "{\"ballot\":\"" + ballot + "\"}"));
    }

    private ResultActions vote(String path, String electionId, UUID voter, String ballot, String... candidateIds) throws Exception {
        String ids = String.join("\",\"", candidateIds);
        return mockMvc.perform(json(post(path, clubId, electionId), TestTokens.student(voter),
                "{\"ballot\":\"" + ballot + "\",\"candidateIds\":[\"" + ids + "\"]}"));
    }

    private ResultActions approve(String requestId, String token) throws Exception {
        return mockMvc.perform(as(post("/api/clubs/{clubId}/approvals/{requestId}/approve", clubId, requestId), token))
                .andExpect(status().isOk());
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
