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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubProfileChangeTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private final UUID president = UUID.randomUUID();
    private final UUID secretary = UUID.randomUUID();
    private final UUID communicationsOfficer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
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
        club.setName("Profil Kulübü " + UUID.randomUUID());
        club.setAbout("Eski tanıtım");
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(secretary, ClubPosition.GENERAL_SECRETARY);
        seat(communicationsOfficer, ClubPosition.COMMUNICATIONS_OFFICER);
        seat(member, ClubPosition.MEMBER);
    }

    @Test
    void profileChangesGoThroughThePresidentAndTheAdvisor() throws Exception {
        String path = "/api/clubs/{clubId}/profile-change-requests";
        String body = "{\"about\":\"Yeni tanıtım\",\"category\":\"ARTS_CULTURE\",\"contactEmail\":\"kulup@example.edu\","
                + "\"instagramUrl\":\"https://instagram.com/kulup\",\"note\":\"Dönem başı güncellemesi\"}";
        mockMvc.perform(json(post(path, clubId), TestTokens.student(member), body))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(path, clubId), TestTokens.student(communicationsOfficer),
                        "{\"websiteUrl\":\"http://duz-metin.example\"}"))
                .andExpect(status().isBadRequest());

        String created = mockMvc.perform(json(post(path, clubId), TestTokens.student(communicationsOfficer), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CLUB_PROFILE_UPDATE"))
                .andExpect(jsonPath("$.status").value("PENDING_PRESIDENT"))
                .andExpect(jsonPath("$.profileChange.category").value("ARTS_CULTURE"))
                .andExpect(jsonPath("$.profileChange.about").value("Yeni tanıtım"))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(created, "$.id");
        mockMvc.perform(json(post(path, clubId), TestTokens.student(secretary), "{\"about\":\"x\"}"))
                .andExpect(status().isConflict());

        approve(requestId, TestTokens.student(president)).andExpect(jsonPath("$.status").value("PENDING_ADVISOR"));
        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(jsonPath("$.about").value("Eski tanıtım"));
        mockMvc.perform(as(get("/api/clubs/approvals/inbox"), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[?(@.id == '" + requestId + "')].profileChange.contactEmail").value(hasItem("kulup@example.edu")));
        approve(requestId, TestTokens.academician(advisor)).andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(jsonPath("$.about").value("Yeni tanıtım"))
                .andExpect(jsonPath("$.profile.category").value("ARTS_CULTURE"))
                .andExpect(jsonPath("$.profile.instagramUrl").value("https://instagram.com/kulup"));
        mockMvc.perform(get("/api/clubs").param("category", "ARTS_CULTURE"))
                .andExpect(jsonPath("$[*].id").value(hasItem(clubId.toString())));
        mockMvc.perform(get("/api/clubs").param("category", "SPORTS"))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(clubId.toString()))));
    }

    @Test
    void thePresidentsOwnProposalGoesStraightToTheAdvisor() throws Exception {
        mockMvc.perform(json(post("/api/clubs/{clubId}/profile-change-requests", clubId), TestTokens.student(president),
                        "{\"category\":\"SPORTS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_ADVISOR"))
                .andExpect(jsonPath("$.profileChange.about").value("Eski tanıtım"));
    }

    @Test
    void aNewLogoIsPublishedOnlyAfterBothApprovals() throws Exception {
        String path = "/api/clubs/{clubId}/logo-change-requests";
        mockMvc.perform(as(multipart(path, clubId).file(logo()), TestTokens.student(secretary)))
                .andExpect(status().isForbidden());
        String created = mockMvc.perform(as(multipart(path, clubId).file(logo()).param("note", "Yeni tasarım"),
                        TestTokens.student(communicationsOfficer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CLUB_LOGO_CHANGE"))
                .andExpect(jsonPath("$.profileChange.logoUrl").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(created, "$.id");
        String proposedLogo = JsonPath.read(created, "$.profileChange.logoUrl");

        approve(requestId, TestTokens.student(president));
        assertThat(clubRepository.findById(clubId).orElseThrow().getLogoUrl()).isNull();
        approve(requestId, TestTokens.academician(advisor)).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(clubRepository.findById(clubId).orElseThrow().getLogoUrl()).isEqualTo(proposedLogo);
    }

    @Test
    void aRejectedLogoLeavesTheCurrentOneInPlace() throws Exception {
        String created = mockMvc.perform(as(multipart("/api/clubs/{clubId}/logo-change-requests", clubId).file(logo()),
                        TestTokens.student(communicationsOfficer)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(created, "$.id");
        mockMvc.perform(json(post("/api/clubs/{clubId}/approvals/{requestId}/reject", clubId, requestId),
                        TestTokens.student(president), "{\"reason\":\"Renkler kurumsal değil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(clubRepository.findById(clubId).orElseThrow().getLogoUrl()).isNull();
    }

    private ResultActions approve(String requestId, String token) throws Exception {
        return mockMvc.perform(as(post("/api/clubs/{clubId}/approvals/{requestId}/approve", clubId, requestId), token))
                .andExpect(status().isOk());
    }

    private void seat(UUID studentId, ClubPosition position) {
        membershipRepository.save(new ClubMembership(clubId, studentId, position));
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
