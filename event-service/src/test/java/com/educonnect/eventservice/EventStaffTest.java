package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EventIntegrationTest
class EventStaffTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID president = UUID.randomUUID();
    private final UUID coordinator = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID attendee = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventRegistrationRepository registrationRepository;

    @Autowired
    private ClubClient clubClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void grants() {
        given(clubClient.getAccess(any(), any())).willAnswer(call -> {
            UUID user = call.getArgument(1);
            Set<String> permissions = user.equals(president)
                    ? Set.of("CREATE_EVENT", "PREPARE_EVENT", "APPROVE_AS_PRESIDENT", "MANAGE_EVENT_OPERATIONS")
                    : user.equals(coordinator) ? Set.of("PREPARE_EVENT", "MANAGE_EVENT_OPERATIONS") : Set.of();
            boolean isMember = !user.equals(outsider);
            return new ClubAccess(clubId, user, null, isMember, user.equals(president), false, permissions);
        });
        given(clubClient.getClubLeaderIds(clubId)).willReturn(List.of(president));
    }

    @Test
    void assignedStaffScanOnlyTheirOwnEventAfterThePresidentApproves() throws Exception {
        Event live = event();
        Event other = event();
        String ticket = register(live, attendee);
        String otherTicket = register(other, UUID.randomUUID());

        verify(member, ticket).andExpect(status().isForbidden());
        staff(post("/api/events/manage/{id}/staff", live.getId()), coordinator, "{\"userId\":\"" + member + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        staff(post("/api/events/manage/{id}/staff", live.getId()), coordinator, "{\"userId\":\"" + outsider + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOT_MEMBER"));
        staff(post("/api/events/manage/{id}/staff", live.getId()), coordinator, "{\"userId\":\"" + member + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_EXISTS"));
        staff(post("/api/events/manage/{id}/staff", live.getId()), member, "{\"userId\":\"" + coordinator + "\"}")
                .andExpect(status().isForbidden());
        assertThat(notifications(live, "EVENT_STAFF_REQUEST")).singleElement().asString().contains(president.toString());

        verify(member, ticket).andExpect(status().isForbidden());
        staff(post("/api/events/manage/{id}/staff/{user}/approve", live.getId(), member), coordinator, "")
                .andExpect(status().isForbidden());
        staff(post("/api/events/manage/{id}/staff/{user}/approve", live.getId(), member), president, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value(president.toString()));
        assertThat(notifications(live, "EVENT_STAFF_ASSIGNED")).singleElement().asString().contains(member.toString());

        verify(member, ticket).andExpect(status().isOk());
        verify(member, otherTicket).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/events/manage/{id}/registrations/{student}/check-in", other.getId(), attendee),
                        TestTokens.student(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/events/manage/staff/me"), TestTokens.student(member)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(live.getId().toString()));
        mockMvc.perform(as(get("/api/events/manage/{id}/staff", live.getId()), TestTokens.student(coordinator)))
                .andExpect(jsonPath("$[0].userId").value(member.toString()));

        staff(delete("/api/events/manage/{id}/staff/{user}", live.getId(), member), coordinator, "")
                .andExpect(status().isNoContent());
        verify(member, register(live, UUID.randomUUID())).andExpect(status().isForbidden());
    }

    @Test
    void thePresidentsOwnAssignmentNeedsNoApproval() throws Exception {
        Event live = event();
        staff(post("/api/events/manage/{id}/staff", live.getId()), president, "{\"userId\":\"" + coordinator + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        staff(post("/api/events/manage/{id}/staff/{user}/reject", live.getId(), coordinator), president, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOT_PENDING"));
    }

    private ResultActions verify(UUID scanner, String qr) throws Exception {
        return mockMvc.perform(as(post("/api/events/manage/verify-qr").param("qrCode", qr), TestTokens.student(scanner)));
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions staff(B request, UUID actor, String body) throws Exception {
        B prepared = as(request, TestTokens.student(actor));
        if (!body.isEmpty()) {
            prepared.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(prepared);
    }

    private List<String> notifications(Event event, String type) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from event_db.outbox_messages "
                        + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ? "
                        + "and convert_from(body, 'UTF8') like ?",
                String.class, "%" + event.getId() + "%", "%\"type\":\"" + type + "\"%");
    }

    private String register(Event event, UUID student) {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(event.getId());
        registration.setStudentId(student);
        registration.setQrCode(UUID.randomUUID().toString());
        return registrationRepository.save(registration).getQrCode();
    }

    private Event event() {
        Event event = new Event();
        event.setTitle("Görevli Etkinliği " + UUID.randomUUID());
        event.setStartsAt(LocalDateTime.now().minusMinutes(10).withNano(0));
        event.setEndsAt(LocalDateTime.now().plusHours(2).withNano(0));
        event.setLocation("B-101");
        event.setClubId(clubId);
        event.setClubName("Kulüp");
        event.setStatus(EventStatus.ACTIVE);
        event.setPublishedAt(LocalDateTime.now());
        return eventRepository.save(event);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
