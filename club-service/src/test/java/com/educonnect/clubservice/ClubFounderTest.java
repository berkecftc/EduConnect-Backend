package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubCreationRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.common.test.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
@TestPropertySource(properties = "educonnect.club.founding.min-members=3")
class ClubFounderTest {

    private final UUID requester = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID academician = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubCreationRequestRepository requestRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @MockitoBean
    private UserClient userClient;

    @BeforeEach
    void stubUsers() {
        given(userClient.getAcademicianById(any())).willAnswer(call -> {
            AcademicianSummary summary = new AcademicianSummary();
            summary.setId(call.getArgument(0));
            summary.setRole("ACADEMICIAN");
            return summary;
        });
        given(userClient.getUsersByIds(any())).willAnswer(call -> ((Collection<UUID>) call.getArgument(0)).stream()
                .map(id -> {
                    UserSummary user = new UserSummary();
                    user.setId(id);
                    user.setFirstName("Kurucu");
                    user.setStudentNumber(id.equals(academician) ? null : "2026" + id.toString().substring(0, 4));
                    return user;
                })
                .toList());
    }

    @Test
    void foundersMustReachTheMinimumAndConfirmBeforeTheAdvisorDecides() throws Exception {
        mockMvc.perform(json(post("/api/clubs/request-creation"), TestTokens.student(requester), body(Set.of(first))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FOUNDERS_REQUIRED"));
        mockMvc.perform(json(post("/api/clubs/request-creation"), TestTokens.student(requester), body(Set.of(first, academician))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FOUNDER"));

        mockMvc.perform(json(post("/api/clubs/request-creation"), TestTokens.student(requester), body(Set.of(first, second))))
                .andExpect(status().isOk());
        ClubCreationRequest request = requestOf(requester);
        assertThat(request.getStatus()).isEqualTo(ClubCreationRequestStatus.PENDING_FOUNDERS);
        mockMvc.perform(as(get("/api/academician/club-creation-requests"), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[*].id", not(hasItem(request.getId().toString()))));
        mockMvc.perform(as(get("/api/clubs/founding-invitations"), TestTokens.student(first)))
                .andExpect(jsonPath("$[*].id", hasItem(request.getId().toString())));

        String confirm = "/api/clubs/creation-requests/{requestId}/founders/confirm";
        mockMvc.perform(as(post(confirm, request.getId()), TestTokens.student(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_FOUNDERS"));
        mockMvc.perform(as(post(confirm, request.getId()), TestTokens.student(first)))
                .andExpect(status().isConflict());
        mockMvc.perform(as(post(confirm, request.getId()), TestTokens.student(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.founders", hasSize(3)));

        mockMvc.perform(as(get("/api/academician/club-creation-requests"), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[?(@.id == '" + request.getId() + "')].founders[*].status", hasSize(3)));
        String club = mockMvc.perform(as(put("/api/academician/club-creation-requests/{id}/approve", request.getId()),
                        TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID clubId = UUID.fromString(JsonPath.read(club, "$.id"));

        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, first).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.MEMBER);
        assertThat(membershipRepository.findByClubIdAndStudentId(clubId, requester).orElseThrow().getClubRole())
                .isEqualTo(ClubPosition.PRESIDENT);
        mockMvc.perform(as(get("/api/clubs/{clubId}/founders", clubId), TestTokens.student(UUID.randomUUID())))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void aDeclineBelowTheMinimumClosesTheRequest() throws Exception {
        mockMvc.perform(json(post("/api/clubs/request-creation"), TestTokens.student(requester), body(Set.of(first, second))))
                .andExpect(status().isOk());
        ClubCreationRequest request = requestOf(requester);
        mockMvc.perform(as(post("/api/clubs/creation-requests/{requestId}/founders/decline", request.getId()), TestTokens.student(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(as(get("/api/clubs/my-creation-requests"), TestTokens.student(requester)))
                .andExpect(jsonPath("$[0].status").value("REJECTED"));
    }

    private ClubCreationRequest requestOf(UUID studentId) {
        return requestRepository.findAll().stream()
                .filter(found -> found.getRequestingStudentId().equals(studentId))
                .findFirst()
                .orElseThrow();
    }

    private String body(Set<UUID> founders) {
        StringBuilder ids = new StringBuilder();
        founders.forEach(id -> ids.append(ids.isEmpty() ? "" : ",").append('"').append(id).append('"'));
        return "{\"name\":\"Kurucu Kulübü " + UUID.randomUUID() + "\",\"academicAdvisorId\":\"" + advisor
                + "\",\"founderIds\":[" + ids + "]}";
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
