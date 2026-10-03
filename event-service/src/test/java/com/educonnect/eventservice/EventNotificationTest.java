package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.AdmissionMode;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventAudience;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.service.EventChangeService;
import com.educonnect.eventservice.service.EventLifecycleService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EventIntegrationTest
class EventNotificationTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID president = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID otherStudent = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventRegistrationRepository registrationRepository;

    @Autowired
    private EventParticipationRequestRepository requestRepository;

    @Autowired
    private EventChangeService changeService;

    @Autowired
    private EventLifecycleService lifecycleService;

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
        given(clubClient.getClubAdvisorId(clubId)).willReturn(advisor);
        given(clubClient.getClubLeaderIds(clubId)).willReturn(List.of(president));
    }

    @Test
    void theApprovalChainTellsTheNextDeciderAndTheOrganizerTheOutcome() throws Exception {
        Event draft = event(EventStatus.PENDING_PRESIDENT, AdmissionMode.APPROVAL_REQUIRED, null);
        student(post("/api/events/president/{id}/approve", draft.getId()), president, "{}").andExpect(status().isOk());
        assertThat(notifications(draft, "EVENT_APPROVAL_REQUEST")).singleElement().asString()
                .contains(advisor.toString(), "Danışman onayı bekleyen etkinlik", "\"category\":\"CLUB_MANAGEMENT\"");

        mockMvc.perform(as(post("/api/events/advisor/{id}/reject", draft.getId()), TestTokens.academician(advisor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Salon dolu\"}"))
                .andExpect(status().isOk());
        assertThat(notifications(draft, "EVENT_REJECTED")).singleElement().asString()
                .contains(officer.toString(), "kulüp danışmanı", "Salon dolu", "\"link\":\"/events/" + draft.getId() + "\"");

        student(put("/api/events/manage/{id}", draft.getId()), officer, "{\"note\":\"Salon değişti\",\"location\":\"C-1\"}")
                .andExpect(status().isOk());
        assertThat(notifications(draft, "EVENT_APPROVAL_REQUEST")).hasSize(2)
                .allSatisfy(body -> assertThat(body).contains(advisor.toString()).doesNotContain(officer.toString()));

        Event pending = event(EventStatus.PENDING, AdmissionMode.APPROVAL_REQUIRED, null);
        mockMvc.perform(as(post("/api/events/advisor/{id}/approve", pending.getId()), TestTokens.academician(advisor)))
                .andExpect(status().isOk());
        assertThat(notifications(pending, "EVENT_APPROVED")).singleElement().asString()
                .contains(officer.toString(), "Etkinliğiniz yayımlandı");
    }

    @Test
    void participationDecisionsAndWaitlistPromotionsReachTheStudent() throws Exception {
        Event guarded = event(EventStatus.ACTIVE, AdmissionMode.APPROVAL_REQUIRED, null);
        student(post("/api/events/{id}/participation-request", guarded.getId()), student, "{}").andExpect(status().isCreated());
        assertThat(notifications(guarded, "EVENT_PARTICIPATION_REQUEST")).singleElement().asString()
                .contains(officer.toString(), "Yeni katılım talebi");
        UUID requestId = requestRepository.findByEventIdAndStudentId(guarded.getId(), student).orElseThrow().getId();
        student(post("/api/events/participation-requests/{id}/reject", requestId), officer, "{\"reason\":\"Kontenjan ayrıldı\"}")
                .andExpect(status().isOk());
        assertThat(notifications(guarded, "EVENT_REQUEST_REJECTED")).singleElement().asString()
                .contains(student.toString(), "\"category\":\"EVENT\"", "Kontenjan ayrıldı");

        Event small = event(EventStatus.ACTIVE, AdmissionMode.AUTO_CONFIRM, 1);
        student(post("/api/events/{id}/participation-request", small.getId()), student, "{}").andExpect(status().isCreated());
        student(post("/api/events/{id}/participation-request", small.getId()), otherStudent, "{}").andExpect(status().isCreated());
        assertThat(registered(small)).singleElement().asString().contains(student.toString(), "\"origin\":\"SELF\"");
        mockMvc.perform(as(delete("/api/events/{id}/registration", small.getId()), TestTokens.student(student)))
                .andExpect(status().is2xxSuccessful());
        assertThat(registered(small)).hasSize(2)
                .anySatisfy(body -> assertThat(body).contains(otherStudent.toString(), "\"origin\":\"WAITLIST_PROMOTED\""));
    }

    @Test
    void closingTheClubCancelsItsEventsAndTellsRegistrantsAndOpenRequesters() {
        Event active = event(EventStatus.ACTIVE, AdmissionMode.APPROVAL_REQUIRED, null);
        EventRegistration registration = new EventRegistration();
        registration.setEventId(active.getId());
        registration.setStudentId(student);
        registration.setQrCode(UUID.randomUUID().toString());
        registrationRepository.save(registration);
        EventParticipationRequest waiting = new EventParticipationRequest(active.getId(), otherStudent);
        waiting.setStatus(ParticipationRequestStatus.PENDING);
        requestRepository.save(waiting);
        Event draft = event(EventStatus.PENDING, AdmissionMode.APPROVAL_REQUIRED, null);

        assertThat(changeService.cancelForClosedClub(clubId)).isEqualTo(2);

        assertThat(eventRepository.findById(active.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(eventRepository.findById(draft.getId()).orElseThrow().getCancellationReason()).isEqualTo("Kulüp kapatıldı.");
        assertThat(requestRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(ParticipationRequestStatus.CLOSED);
        assertThat(outbox("event.changed", active)).singleElement().asString()
                .contains(student.toString(), otherStudent.toString(), "CANCELLED", "Kulüp kapatıldı.");
    }

    @Test
    void eventsThatMissTheirApprovalTellTheOrganizer() {
        Event late = event(EventStatus.PENDING, AdmissionMode.APPROVAL_REQUIRED, null);
        late.setStartsAt(LocalDateTime.now().minusHours(1));
        late.setEndsAt(LocalDateTime.now().plusHours(1));
        eventRepository.save(late);

        lifecycleService.advance(LocalDateTime.now());

        assertThat(notifications(late, "EVENT_EXPIRED")).singleElement().asString()
                .contains(officer.toString(), "onaylanmadan");
    }

    private List<String> notifications(Event event, String type) {
        return outbox("notification.request", event).stream()
                .filter(body -> body.contains("\"type\":\"" + type + "\""))
                .toList();
    }

    private List<String> registered(Event event) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from event_db.outbox_messages "
                        + "where routing_key = 'event.registered' and convert_from(body, 'UTF8') like ?",
                String.class, "%\"eventTitle\":\"" + event.getTitle() + "\"%");
    }

    private List<String> outbox(String routingKey, Event event) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from event_db.outbox_messages "
                        + "where routing_key = ? and convert_from(body, 'UTF8') like ?",
                String.class, routingKey, "%" + event.getId() + "%");
    }

    private Event event(EventStatus status, AdmissionMode admission, Integer capacity) {
        Event event = new Event();
        event.setTitle("Bildirimli Etkinlik " + UUID.randomUUID());
        event.setStartsAt(LocalDateTime.now().plusDays(5).withNano(0));
        event.setEndsAt(event.getStartsAt().plusHours(2));
        event.setLocation("B-101");
        event.setClubId(clubId);
        event.setClubName("Kulüp");
        event.setCreatedByStudentId(officer);
        event.setAudience(EventAudience.ALL_STUDENTS);
        event.setAdmission(admission);
        event.setCapacity(capacity);
        event.setStatus(status);
        if (status == EventStatus.ACTIVE) {
            event.setPublishedAt(LocalDateTime.now());
        }
        return eventRepository.save(event);
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions student(B request, UUID actor, String body) throws Exception {
        return mockMvc.perform(as(request, TestTokens.student(actor)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
