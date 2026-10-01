package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.service.MembershipTerms;
import com.educonnect.common.test.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubMeetingTest {

    private final UUID president = UUID.randomUUID();
    private final UUID secretary = UUID.randomUUID();
    private final UUID boardMember = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @Autowired
    private MembershipTerms membershipTerms;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Tutanak Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(secretary, ClubPosition.GENERAL_SECRETARY);
        seat(boardMember, ClubPosition.BOARD_MEMBER);
        seat(member, ClubPosition.MEMBER);
    }

    @Test
    void approvedMinutesAreNumberedIntoTheDecisionBook() throws Exception {
        String path = "/api/clubs/{clubId}/meetings";
        LocalDateTime meetingAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MINUTES);
        String yesterday = meetingAt.toString();
        String year = String.valueOf(membershipTerms.academicYearOf(meetingAt.toLocalDate()));
        String body = meeting(yesterday, president + "\",\"" + secretary, "\"Bahar şenliği düzenlenecek\",\"Bütçe taslağı kabul edildi\"");
        mockMvc.perform(json(post(path, clubId), TestTokens.student(member), body)).andExpect(status().isForbidden());
        mockMvc.perform(json(post(path, clubId), TestTokens.student(secretary), meeting(yesterday, member.toString(), "\"x\"")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(path, clubId), TestTokens.student(secretary),
                        meeting(LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MINUTES).toString(), secretary.toString(), "\"x\"")))
                .andExpect(status().isBadRequest());

        String created = mockMvc.perform(json(post(path, clubId), TestTokens.student(secretary), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CLUB_MEETING_MINUTES"))
                .andExpect(jsonPath("$.status").value("PENDING_PRESIDENT"))
                .andExpect(jsonPath("$.meeting.quorumMet").value(true))
                .andExpect(jsonPath("$.meeting.decisions", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(created, "$.id");
        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-book", clubId).param("academicYear", year), TestTokens.student(president)))
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(as(post("/api/clubs/{clubId}/approvals/{requestId}/approve", clubId, requestId), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(json(post(path, clubId), TestTokens.student(president),
                        meeting(yesterday, boardMember.toString(), "\"Yeni üyelik dönemi başlatıldı\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.meeting.quorumMet").value(false));

        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-book", clubId).param("academicYear", year), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].number", contains(1, 2, 3)))
                .andExpect(jsonPath("$[0].text").value("Bahar şenliği düzenlenecek"))
                .andExpect(jsonPath("$[2].label", endsWith("/3")));
        mockMvc.perform(as(get("/api/clubs/{clubId}/meetings", clubId).param("academicYear", year), TestTokens.student(boardMember)))
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-book", clubId).param("academicYear", year), TestTokens.student(member)))
                .andExpect(status().isForbidden());
    }

    private static String meeting(String at, String attendees, String decisions) {
        return "{\"meetingAt\":\"" + at + "\",\"location\":\"B-101\",\"agenda\":\"Dönem planı\","
                + "\"minutes\":\"Toplantı açıldı, gündem görüşüldü.\",\"attendeeIds\":[\"" + attendees + "\"],"
                + "\"decisions\":[" + decisions + "]}";
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
