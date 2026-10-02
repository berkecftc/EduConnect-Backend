package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.model.RegistrationStatus;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EventIntegrationTest
class EventChangeTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID president = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID attendee = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventRegistrationRepository registrationRepository;

    @Autowired
    private EventParticipationRequestRepository requestRepository;

    @Autowired
    private ClubClient clubClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void grants() {
        given(clubClient.getAccess(any(), any())).willAnswer(call -> {
            UUID user = call.getArgument(1);
            Set<String> permissions = user.equals(president) ? Set.of("CREATE_EVENT", "PREPARE_EVENT", "APPROVE_AS_PRESIDENT", "MANAGE_EVENT_OPERATIONS")
                    : user.equals(officer) ? Set.of("PREPARE_EVENT", "MANAGE_EVENT_OPERATIONS")
                    : user.equals(advisor) ? Set.of("ADVISE")
                    : Set.of();
            return new ClubAccess(clubId, user, null, true, user.equals(president), user.equals(advisor), permissions);
        });
    }

    @Test
    void draftsAreEditedAndRejectedEventsComeBackWithANote() throws Exception {
        Event pending = event(EventStatus.PENDING);
        json(put("/api/events/manage/{id}", pending.getId()), member, "{\"title\":\"Hacklendi\"}").andExpect(status().isForbidden());
        json(put("/api/events/manage/{id}", pending.getId()), officer, "{\"title\":\"Yeni Başlık\",\"capacity\":30}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Yeni Başlık"))
                .andExpect(jsonPath("$.capacity").value(30));

        Event rejected = event(EventStatus.REJECTED);
        json(put("/api/events/manage/{id}", rejected.getId()), officer, "{\"location\":\"C-1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESUBMISSION_NOTE_REQUIRED"));
        json(put("/api/events/manage/{id}", rejected.getId()), officer, "{\"location\":\"C-1\",\"note\":\"Salon değişti\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        Event active = event(EventStatus.ACTIVE);
        json(put("/api/events/manage/{id}", active.getId()), president, "{\"title\":\"X\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_EDITABLE"));
        mockMvc.perform(as(get("/api/events/manage/{id}/changes", rejected.getId()), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$[0].kind").value("RESUBMITTED"))
                .andExpect(jsonPath("$[0].reason").value("Salon değişti"));
        mockMvc.perform(as(get("/api/events/manage/{id}/changes", rejected.getId()), TestTokens.student(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postponingKeepsRegistrationsGoesBackToTheAdvisorAndNotifiesParticipants() throws Exception {
        Event active = event(EventStatus.ACTIVE);
        register(active, attendee);
        LocalDateTime newStart = active.getStartsAt().plusDays(7);
        String body = "{\"startsAt\":\"" + newStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",\"reason\":\"Salon bakımda\"}";

        json(post("/api/events/manage/{id}/postpone", active.getId()), officer, body).andExpect(status().isForbidden());
        json(post("/api/events/manage/{id}/postpone", active.getId()), president, "{\"reason\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NEW_START_REQUIRED"));
        json(post("/api/events/manage/{id}/postpone", active.getId()), president, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.endsAt").value(newStart.plusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        assertThat(registrationRepository.findByEventIdAndStudentId(active.getId(), attendee).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(outbox("event.changed", active.getId())).isEqualTo(1);

        int announcements = outbox("event.created", active.getId());
        mockMvc.perform(as(post("/api/events/advisor/{id}/approve", active.getId()), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        assertThat(eventRepository.findById(active.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.ACTIVE);
        assertThat(outbox("event.created", active.getId())).isEqualTo(announcements);

        json(post("/api/events/manage/{id}/relocate", active.getId()), president, "{\"location\":\"Konferans Salonu\",\"reason\":\"Kalabalık\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Konferans Salonu"));
        assertThat(outbox("event.changed", active.getId())).isEqualTo(2);
    }

    @Test
    void theAdvisorCancelsWithAReasonAndOpenRequestsClose() throws Exception {
        Event active = event(EventStatus.ACTIVE);
        register(active, attendee);
        EventParticipationRequest waiting = new EventParticipationRequest(active.getId(), UUID.randomUUID());
        requestRepository.save(waiting);

        json(post("/api/events/manage/{id}/cancel", active.getId()), officer, "{\"reason\":\"x\"}").andExpect(status().isForbidden());
        json(post("/api/events/manage/{id}/cancel", active.getId()), advisor, "{}").andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/events/manage/{id}/cancel", active.getId()), TestTokens.academician(advisor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Konuşmacı gelemiyor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Konuşmacı gelemiyor"));
        assertThat(requestRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(ParticipationRequestStatus.CLOSED);
        assertThat(outbox("event.changed", active.getId())).isEqualTo(1);
        json(post("/api/events/manage/{id}/cancel", active.getId()), president, "{\"reason\":\"tekrar\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_CHANGEABLE"));
        mockMvc.perform(as(get("/api/events/my-registrations"), TestTokens.student(attendee)))
                .andExpect(jsonPath("$[0].eventStatus").value("CANCELLED"));
    }

    private int outbox(String routingKey, UUID eventId) {
        return jdbcTemplate.queryForObject("select count(*) from event_db.outbox_messages where routing_key = ? "
                + "and convert_from(body, 'UTF8') like ?", Integer.class, routingKey, "%" + eventId + "%");
    }

    private void register(Event event, UUID student) {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(event.getId());
        registration.setStudentId(student);
        registration.setQrCode(UUID.randomUUID().toString());
        registrationRepository.save(registration);
    }

    private Event event(EventStatus status) {
        Event event = new Event();
        event.setTitle("Değişen Etkinlik");
        event.setStartsAt(LocalDateTime.now().plusDays(5).withNano(0));
        event.setEndsAt(event.getStartsAt().plusHours(2));
        event.setLocation("B-101");
        event.setClubId(clubId);
        event.setClubName("Kulüp");
        event.setStatus(status);
        if (status == EventStatus.REJECTED) {
            event.setRejectionReason("Afiş uygunsuz");
        }
        if (status == EventStatus.ACTIVE) {
            event.setPublishedAt(LocalDateTime.now());
        }
        return eventRepository.save(event);
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions json(B request, UUID actor, String body) throws Exception {
        return mockMvc.perform(as(request, TestTokens.student(actor)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
