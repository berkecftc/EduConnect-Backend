package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubAnnouncementTest {

    private final UUID president = UUID.randomUUID();
    private final UUID boardMember = UUID.randomUUID();
    private final UUID communicationsOfficer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Duyuru Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(boardMember, ClubPosition.BOARD_MEMBER);
        seat(communicationsOfficer, ClubPosition.COMMUNICATIONS_OFFICER);
        seat(member, ClubPosition.MEMBER);
    }

    @Test
    void announcementsArePublishedToMembersOnceThePresidentApproves() throws Exception {
        String path = "/api/clubs/{clubId}/announcements";
        String body = "{\"title\":\"Tanışma toplantısı\",\"body\":\"Cuma 18.00'de B-101'de buluşuyoruz.\"}";
        mockMvc.perform(json(post(path, clubId), TestTokens.student(member), body))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(path, clubId), TestTokens.student(communicationsOfficer), "{\"title\":\"\",\"body\":\"x\"}"))
                .andExpect(status().isBadRequest());

        String created = mockMvc.perform(json(post(path, clubId), TestTokens.student(communicationsOfficer), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CLUB_ANNOUNCEMENT"))
                .andExpect(jsonPath("$.status").value("PENDING_PRESIDENT"))
                .andExpect(jsonPath("$.announcement.title").value("Tanışma toplantısı"))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(created, "$.id");
        mockMvc.perform(as(get(path, clubId), TestTokens.student(member)))
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(as(get("/api/clubs/approvals/inbox"), TestTokens.student(president)))
                .andExpect(jsonPath("$[*].announcement.title").value(hasItem("Tanışma toplantısı")));

        mockMvc.perform(as(post("/api/clubs/{clubId}/approvals/{requestId}/approve", clubId, requestId), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(as(get(path, clubId), TestTokens.student(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Tanışma toplantısı"))
                .andExpect(jsonPath("$.content[0].publishedAt").isNotEmpty());
        mockMvc.perform(as(get(path, clubId), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$.content[0].title").value("Tanışma toplantısı"));
        mockMvc.perform(as(get(path, clubId), TestTokens.student(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void thePresidentsOwnAnnouncementIsPublishedRightAway() throws Exception {
        mockMvc.perform(json(post("/api/clubs/{clubId}/announcements", clubId), TestTokens.student(president),
                        "{\"title\":\"Genel kurul\",\"body\":\"Genel kurul tarihi açıklandı.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(as(get("/api/clubs/{clubId}/announcements", clubId), TestTokens.student(member)))
                .andExpect(jsonPath("$.content[0].title").value("Genel kurul"));
    }

    @Test
    void rejectedAnnouncementsStayHiddenAndPublishedOnesCanBeRemoved() throws Exception {
        String created = mockMvc.perform(json(post("/api/clubs/{clubId}/announcements", clubId), TestTokens.student(boardMember),
                        "{\"title\":\"Taslak\",\"body\":\"Henüz hazır değil.\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(created, "$.id");
        mockMvc.perform(json(post("/api/clubs/{clubId}/approvals/{requestId}/reject", clubId, requestId),
                        TestTokens.student(president), "{\"reason\":\"Tarih netleşmedi\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(as(get("/api/clubs/{clubId}/announcements", clubId), TestTokens.student(member)))
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(json(post("/api/clubs/{clubId}/announcements", clubId), TestTokens.student(president),
                        "{\"title\":\"Yanlış duyuru\",\"body\":\"x\"}"))
                .andExpect(status().isCreated());
        String listed = mockMvc.perform(as(get("/api/clubs/{clubId}/announcements", clubId), TestTokens.student(member)))
                .andReturn().getResponse().getContentAsString();
        String announcementId = JsonPath.read(listed, "$.content[0].id");
        mockMvc.perform(as(delete("/api/clubs/{clubId}/announcements/{id}", clubId, announcementId), TestTokens.student(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/clubs/{clubId}/announcements/{id}", clubId, announcementId), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/clubs/{clubId}/announcements", clubId), TestTokens.student(member)))
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(as(get("/api/clubs/{clubId}/decision-log", clubId), TestTokens.student(president)))
                .andExpect(jsonPath("$[*].action").value(hasItem("ANNOUNCEMENT_REMOVED")));
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
