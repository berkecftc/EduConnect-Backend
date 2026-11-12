package com.educonnect.clubservice;

import com.educonnect.clubservice.client.EventClient;
import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.response.ClubEventStatistics;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubReportTest {

    private final UUID president = UUID.randomUUID();
    private final UUID secretary = UUID.randomUUID();
    private final UUID treasurer = UUID.randomUUID();
    private final UUID auditor = UUID.randomUUID();
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

    @Autowired
    private EventClient eventClient;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;
    private int year;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Rapor Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(secretary, ClubPosition.GENERAL_SECRETARY);
        seat(treasurer, ClubPosition.TREASURER);
        seat(auditor, ClubPosition.AUDITOR);
        seat(member, ClubPosition.MEMBER);
        year = membershipTerms.currentAcademicYear();
        given(eventClient.clubStatistics(eq(clubId), any(), any()))
                .willReturn(new ClubEventStatistics(5, 3, 1, 1, 0, 0, 40, 30));
    }

    @Test
    void theActivityReportIsDraftedTogetherAndApprovedByThePresidentAndTheAdvisor() throws Exception {
        String activity = "/api/clubs/{clubId}/reports/activity/{year}";
        mockMvc.perform(json(put(activity, clubId, year), TestTokens.student(member), "{\"summary\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put(activity, clubId, year - 2), TestTokens.student(secretary), "{\"summary\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(put(activity, clubId, year), TestTokens.student(secretary), "{\"summary\":\"Yıl boyunca 5 etkinlik yaptık.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(json(put(activity + "/finance-note", clubId, year), TestTokens.student(treasurer),
                        "{\"financeNote\":\"Bütçe dengeli kapandı.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.body").value("Yıl boyunca 5 etkinlik yaptık."))
                .andExpect(jsonPath("$.financeNote").value("Bütçe dengeli kapandı."));
        mockMvc.perform(as(get("/api/clubs/{clubId}/reports/preview/{year}", clubId, year), TestTokens.student(secretary)))
                .andExpect(jsonPath("$.events").value(5))
                .andExpect(jsonPath("$.activeMembers").value(5));

        String first = submit("activity", TestTokens.student(secretary))
                .andExpect(jsonPath("$.type").value("CLUB_ACTIVITY_REPORT"))
                .andExpect(jsonPath("$.status").value("PENDING_PRESIDENT"))
                .andExpect(jsonPath("$.report.snapshot.attendances").value(30))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(json(put(activity, clubId, year), TestTokens.student(secretary), "{\"summary\":\"değişiklik\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(json(post("/api/clubs/{clubId}/approvals/{requestId}/reject", clubId, JsonPath.read(first, "$.id")),
                        TestTokens.student(president), "{\"reason\":\"Etkinlik listesi eksik\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(get(activity, clubId, year), TestTokens.student(auditor)))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        String second = JsonPath.read(submit("activity", TestTokens.student(secretary)).andReturn().getResponse().getContentAsString(), "$.id");
        approve(second, TestTokens.student(president)).andExpect(jsonPath("$.status").value("PENDING_ADVISOR"));
        approve(second, TestTokens.academician(advisor)).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(as(get(activity, clubId, year), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.snapshot.completedEvents").value(3));
        mockMvc.perform(as(get("/api/clubs/{clubId}/reports", clubId), TestTokens.student(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAuditReportIsIndependentOfTheBoardAndGoesStraightToTheAdvisor() throws Exception {
        String audit = "/api/clubs/{clubId}/reports/audit/{year}";
        mockMvc.perform(json(put(audit, clubId, year), TestTokens.student(secretary), "{\"findings\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put(audit, clubId, year), TestTokens.student(auditor), "{\"recommendations\":\"Belgeler eksiksiz tutulmalı.\"}"))
                .andExpect(status().isOk());
        submit("audit", TestTokens.student(auditor), status().isBadRequest());
        mockMvc.perform(json(put(audit, clubId, year), TestTokens.student(auditor),
                        "{\"findings\":\"Kayıtlar belgeleriyle uyumlu.\",\"recommendations\":\"Belgeler eksiksiz tutulmalı.\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(get(audit, clubId, year), TestTokens.student(president))).andExpect(status().isNotFound());

        String requestId = JsonPath.read(submit("audit", TestTokens.student(auditor))
                .andExpect(jsonPath("$.status").value("PENDING_ADVISOR"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(as(get(audit, clubId, year), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        approve(requestId, TestTokens.academician(advisor)).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private ResultActions submit(String type, String token) throws Exception {
        return submit(type, token, status().isCreated());
    }

    private ResultActions submit(String type, String token, ResultMatcher expected) throws Exception {
        return mockMvc.perform(as(post("/api/clubs/{clubId}/reports/{type}/{year}/submit", clubId, type, year), token))
                .andExpect(expected);
    }

    private ResultActions approve(String requestId, String token) throws Exception {
        return mockMvc.perform(as(post("/api/clubs/{clubId}/approvals/{requestId}/approve", clubId, requestId), token))
                .andExpect(status().isOk());
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
