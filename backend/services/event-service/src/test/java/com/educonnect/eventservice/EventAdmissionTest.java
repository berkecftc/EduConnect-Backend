package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.AdmissionMode;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventAudience;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.RegistrationStatus;
import com.educonnect.eventservice.repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import com.educonnect.eventservice.service.EventLifecycleService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EventIntegrationTest
class EventAdmissionTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID ayse = UUID.randomUUID();
    private final UUID burak = UUID.randomUUID();
    private final UUID cem = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventRegistrationRepository registrationRepository;

    @Autowired
    private EventParticipationRequestRepository requestRepository;

    @Autowired
    private EventLifecycleService lifecycleService;

    @Autowired
    private ClubClient clubClient;

    @Test
    void openEventsConfirmUntilFullThenWaitlistAndPromoteOnCancellation() throws Exception {
        UUID eventId = event(EventAudience.ALL_STUDENTS, AdmissionMode.AUTO_CONFIRM, 2, null, null).getId();

        join(eventId, ayse).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
        join(eventId, burak).andExpect(jsonPath("$.status").value("APPROVED"));
        join(eventId, cem).andExpect(jsonPath("$.status").value("WAITLISTED"));
        mockMvc.perform(as(post("/api/events/{id}/participation-request", eventId), TestTokens.academician(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("STUDENTS_ONLY"));
        mockMvc.perform(as(get("/api/events/{id}/availability", eventId), TestTokens.student(ayse)))
                .andExpect(jsonPath("$.registered").value(2))
                .andExpect(jsonPath("$.waitlisted").value(1))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.registrationOpen").value(true));

        mockMvc.perform(as(delete("/api/events/{id}/registration", eventId), TestTokens.student(ayse)))
                .andExpect(status().isNoContent());
        assertThat(registrationRepository.findByEventIdAndStudentId(eventId, ayse).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registrationRepository.findByEventIdAndStudentId(eventId, cem).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.REGISTERED);
        join(eventId, ayse).andExpect(jsonPath("$.status").value("WAITLISTED"));
        mockMvc.perform(as(delete("/api/events/{id}/participation-request", eventId), TestTokens.student(ayse)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/events/my-registrations"), TestTokens.student(ayse)))
                .andExpect(jsonPath("$[0].registrationStatus").value("CANCELLED"));
    }

    @Test
    void approvalsStopAtCapacityAndWindowsAreEnforced() throws Exception {
        UUID eventId = event(EventAudience.ALL_STUDENTS, AdmissionMode.APPROVAL_REQUIRED, 1, null, null).getId();
        given(clubClient.getAccess(clubId, officer)).willReturn(new ClubAccess(clubId, officer, null, true, false, false,
                Set.of("MANAGE_EVENT_OPERATIONS")));

        String first = JsonPath.read(join(eventId, ayse).andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(), "$.requestId");
        String second = JsonPath.read(join(eventId, burak).andReturn().getResponse().getContentAsString(), "$.requestId");
        mockMvc.perform(as(post("/api/events/participation-requests/{id}/approve", first), TestTokens.student(officer)))
                .andExpect(status().isOk());
        mockMvc.perform(as(post("/api/events/participation-requests/{id}/approve", second), TestTokens.student(officer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EVENT_FULL"));

        UUID notYet = event(EventAudience.ALL_STUDENTS, AdmissionMode.AUTO_CONFIRM, null, LocalDateTime.now().plusDays(1), null).getId();
        join(notYet, ayse).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("REGISTRATION_NOT_OPEN"));

        UUID noRefund = event(EventAudience.ALL_STUDENTS, AdmissionMode.AUTO_CONFIRM, null, null, LocalDateTime.now().minusMinutes(1)).getId();
        join(noRefund, ayse).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(as(delete("/api/events/{id}/registration", noRefund), TestTokens.student(ayse)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANCELLATION_CLOSED"));
    }

    @Test
    void registeredStudentsWhoNeverCheckedInBecomeNoShows() throws Exception {
        Event event = event(EventAudience.ALL_STUDENTS, AdmissionMode.AUTO_CONFIRM, null, null, null);
        join(event.getId(), ayse).andExpect(jsonPath("$.status").value("APPROVED"));
        join(event.getId(), burak).andExpect(jsonPath("$.status").value("APPROVED"));
        var attended = registrationRepository.findByEventIdAndStudentId(event.getId(), burak).orElseThrow();
        attended.setAttended(true);
        registrationRepository.save(attended);

        lifecycleService.advance(event.getEndsAt().plusMinutes(1));

        assertThat(registrationRepository.findByEventIdAndStudentId(event.getId(), ayse).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.NO_SHOW);
        assertThat(registrationRepository.findByEventIdAndStudentId(event.getId(), burak).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(requestRepository.findByEventIdAndStudentId(event.getId(), ayse)).isPresent();
    }

    private ResultActions join(UUID eventId, UUID student) throws Exception {
        return mockMvc.perform(as(post("/api/events/{id}/participation-request", eventId), TestTokens.student(student))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private Event event(EventAudience audience, AdmissionMode admission, Integer capacity,
                        LocalDateTime opensAt, LocalDateTime cancelUntil) {
        Event event = new Event();
        event.setTitle("Kontenjanlı Etkinlik");
        event.setStartsAt(LocalDateTime.now().plusDays(3));
        event.setEndsAt(event.getStartsAt().plusHours(2));
        event.setClubId(clubId);
        event.setClubName("Kulüp");
        event.setStatus(EventStatus.ACTIVE);
        event.setAudience(audience);
        event.setAdmission(admission);
        event.setCapacity(capacity);
        event.setRegistrationOpensAt(opensAt);
        event.setCancelUntil(cancelUntil);
        return eventRepository.save(event);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
