package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EventIntegrationTest
@TestPropertySource(properties = "educonnect.club.approval-chain.enabled=true")
class EventApprovalChainTest {

    private static final Set<String> PRESIDENT = Set.of("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA", "MANAGE_EVENT_OPERATIONS",
            "CREATE_EVENT", "PREPARE_EVENT", "APPROVE_AS_PRESIDENT");
    private static final Set<String> BOARD_MEMBER = Set.of("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA", "MANAGE_EVENT_OPERATIONS",
            "PREPARE_EVENT");
    private static final Set<String> TREASURER = Set.of("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA", "MANAGE_EVENT_OPERATIONS");
    private static final Set<String> ADVISOR = Set.of("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA", "ADVISE");

    private final UUID clubId = UUID.randomUUID();
    private final UUID president = UUID.randomUUID();
    private final UUID boardMember = UUID.randomUUID();
    private final UUID treasurer = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();
    private final Map<List<UUID>, Set<String>> grants = new HashMap<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubClient clubClient;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void seed() {
        grants.put(List.of(clubId, president), PRESIDENT);
        grants.put(List.of(clubId, boardMember), BOARD_MEMBER);
        grants.put(List.of(clubId, treasurer), TREASURER);
        grants.put(List.of(clubId, advisor), ADVISOR);
        given(clubClient.getClubIdByName(any())).willReturn(clubId);
        given(clubClient.getAccess(any(), any())).willAnswer(call -> access(call.getArgument(0), call.getArgument(1)));
        given(clubClient.getUserAccess(any())).willAnswer(call -> grants.keySet().stream()
                .filter(key -> key.get(1).equals(call.getArgument(0)))
                .map(key -> access(key.get(0), key.get(1)))
                .toList());
        given(clubClient.getClubIdsByAdvisorId(any())).willAnswer(call -> grants.entrySet().stream()
                .filter(entry -> entry.getKey().get(1).equals(call.getArgument(0)) && entry.getValue().contains("ADVISE"))
                .map(entry -> entry.getKey().get(0))
                .toList());
    }

    @Test
    void aBoardMembersEventGoesToThePresidentBeforeTheAdvisor() throws Exception {
        String eventId = createdId(TestTokens.student(boardMember));
        assertThat(statusOf(eventId)).isEqualTo(EventStatus.PENDING_PRESIDENT);

        mockMvc.perform(as(get("/api/events/advisor/pending"), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[?(@.id == '" + eventId + "')]").isEmpty());
        mockMvc.perform(as(get("/api/events/president/pending"), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(eventId)));
        mockMvc.perform(as(post("/api/events/president/{id}/approve", eventId), TestTokens.student(boardMember)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/events/advisor/{id}/approve", eventId), TestTokens.academician(advisor)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(post("/api/events/president/{id}/approve", eventId), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(as(post("/api/events/advisor/{id}/approve", eventId), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void thePresidentsOwnEventSkipsThePresidentStage() throws Exception {
        String eventId = createdId(TestTokens.student(president));
        assertThat(statusOf(eventId)).isEqualTo(EventStatus.PENDING);
    }

    @Test
    void theTreasurerCannotPrepareEvents() throws Exception {
        mockMvc.perform(as(multipart("/api/events/manage").file(eventData()).file(poster()), TestTokens.student(treasurer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void thePresidentRejectsWithAReason() throws Exception {
        String eventId = createdId(TestTokens.student(boardMember));

        mockMvc.perform(json(post("/api/events/president/{id}/reject", eventId), TestTokens.student(president), "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/events/president/{id}/reject", eventId), TestTokens.student(president),
                        "{\"reason\":\"Tarih sınav haftasına denk geliyor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Tarih sınav haftasına denk geliyor"));
        mockMvc.perform(as(post("/api/events/president/{id}/approve", eventId), TestTokens.student(president)))
                .andExpect(status().isConflict());
    }

    private String createdId(String token) throws Exception {
        String body = mockMvc.perform(as(multipart("/api/events/manage").file(eventData()).file(poster()), token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"([0-9a-f-]{36})\".*", "$1");
    }

    private EventStatus statusOf(String eventId) {
        return eventRepository.findById(UUID.fromString(eventId)).map(Event::getStatus).orElseThrow();
    }

    private ClubAccess access(UUID club, UUID user) {
        Set<String> permissions = grants.getOrDefault(List.of(club, user), Set.of());
        boolean advisorAccess = permissions.contains("ADVISE");
        return new ClubAccess(club, user, null, !advisorAccess && !permissions.isEmpty(),
                permissions.contains("APPROVE_AS_PRESIDENT"), advisorAccess, permissions);
    }

    private MockMultipartFile eventData() {
        String json = "{\"title\":\"Zincir Etkinliği\",\"eventTime\":\"" + LocalDateTime.now().plusDays(10).withNano(0)
                + "\",\"location\":\"Salon\",\"clubName\":\"Kulüp " + clubId + "\"}";
        return new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile poster() {
        return new MockMultipartFile("poster", "poster.png", MediaType.IMAGE_PNG_VALUE, Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
