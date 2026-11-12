package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubNotificationTest {

    private final UUID president = UUID.randomUUID();
    private final UUID secretary = UUID.randomUUID();
    private final UUID membershipOfficer = UUID.randomUUID();
    private final UUID boardMember = UUID.randomUUID();
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
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Bildirim Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(secretary, ClubPosition.GENERAL_SECRETARY);
        seat(membershipOfficer, ClubPosition.MEMBERSHIP_OFFICER);
        seat(boardMember, ClubPosition.BOARD_MEMBER);
        seat(member, ClubPosition.MEMBER);
    }

    @Test
    void membershipApplicationsReachTheReviewersAndDecisionsTheApplicant() throws Exception {
        mockMvc.perform(json(post("/api/clubs/{clubId}/membership-requests", clubId), TestTokens.student(applicant),
                        "{\"message\":\"Katılmak istiyorum\"}"))
                .andExpect(status().isCreated());

        String request = single("CLUB_MEMBERSHIP");
        assertThat(request).contains(president.toString(), secretary.toString(), membershipOfficer.toString(),
                "\"category\":\"CLUB_MANAGEMENT\"", "Yeni üyelik başvurusu", "\"link\":\"/clubs/" + clubId + "\"");
        assertThat(request).doesNotContain(member.toString(), boardMember.toString(), applicant.toString());
    }

    @Test
    void approvedAnnouncementsGoOutAsOneOptionalClubNewsNotification() throws Exception {
        mockMvc.perform(json(post("/api/clubs/{clubId}/announcements", clubId), TestTokens.student(president),
                        "{\"title\":\"Genel kurul\",\"body\":\"Genel kurul tarihi açıklandı.\"}"))
                .andExpect(status().isCreated());

        String announcement = single("CLUB_ANNOUNCEMENT");
        assertThat(announcement).contains("\"category\":\"CLUB_NEWS\"", "Genel kurul", member.toString(), president.toString(),
                boardMember.toString());
    }

    @Test
    void leavingTellsTheLeaderAndClosingTellsMembersAndOpenApplicants() throws Exception {
        mockMvc.perform(as(delete("/api/clubs/{clubId}/leave", clubId), TestTokens.student(boardMember)))
                .andExpect(status().is2xxSuccessful());
        assertThat(notifications("CLUB_NOTICE")).anySatisfy(body -> assertThat(body)
                .contains("Üye ayrıldı", president.toString()).doesNotContain(boardMember.toString()));

        mockMvc.perform(json(post("/api/clubs/{clubId}/membership-requests", clubId), TestTokens.student(applicant), "{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(json(post("/api/academician/clubs/{clubId}/close", clubId), TestTokens.academician(advisor),
                        "{\"reason\":\"Faaliyet yok\"}"))
                .andExpect(status().isOk());

        assertThat(notifications("CLUB_NOTICE")).anySatisfy(body -> assertThat(body)
                .contains("Kulüp kapatıldı", "Faaliyet yok", president.toString(), member.toString(), secretary.toString()));
        assertThat(notifications("CLUB_MEMBERSHIP")).anySatisfy(body -> assertThat(body)
                .contains("Üyelik başvurunuz kapandı", applicant.toString()));
    }

    @Test
    void servicesCanAskWhoCurrentlyLeadsAClub() throws Exception {
        mockMvc.perform(get("/api/clubs/internal/{clubId}/leader-ids", clubId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.service("event-service"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(president.toString()));
        mockMvc.perform(as(get("/api/clubs/internal/{clubId}/leader-ids", clubId), TestTokens.student(president)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAssistantCatalogListsOnlyActiveClubsAndHearsAboutChanges() throws Exception {
        String catalog = "/api/clubs/internal/catalog";
        String service = TestTokens.bearer(TestTokens.service("llm-service"));
        mockMvc.perform(get(catalog).header(HttpHeaders.AUTHORIZATION, service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + clubId + "')]").value(hasSize(1)));

        mockMvc.perform(json(post("/api/academician/clubs/{clubId}/advisor/resign", clubId), TestTokens.academician(advisor),
                        "{\"reason\":\"Emeklilik\"}"))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(catalog).header(HttpHeaders.AUTHORIZATION, service))
                .andExpect(jsonPath("$[?(@.id == '" + clubId + "')]").isEmpty());
        assertThat(jdbcTemplate.queryForObject("select count(*) from club_db.outbox_messages where routing_key = 'club.catalog.changed' "
                + "and convert_from(body, 'UTF8') like ?", Integer.class, "%" + clubId + "%")).isEqualTo(1);
    }

    private String single(String type) {
        List<String> bodies = notifications(type);
        assertThat(bodies).hasSize(1);
        return bodies.getFirst();
    }

    private List<String> notifications(String type) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from club_db.outbox_messages "
                        + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ? "
                        + "and convert_from(body, 'UTF8') like ?",
                String.class, "%" + clubId + "%", "%\"type\":\"" + type + "\"%");
    }

    private void seat(UUID studentId, ClubPosition position) {
        membershipRepository.save(new ClubMembership(clubId, studentId, position));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
