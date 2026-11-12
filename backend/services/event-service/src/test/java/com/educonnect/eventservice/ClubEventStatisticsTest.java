package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRegistrationRepository;
import com.educonnect.eventservice.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EventIntegrationTest
class ClubEventStatisticsTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2025, 10, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 1, 0, 0);

    private final UUID clubId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventRegistrationRepository registrationRepository;

    @Test
    void clubStatisticsCountEventsAndAttendanceWithinThePeriodForServicesOnly() throws Exception {
        UUID completed = event(EventStatus.COMPLETED, FROM.plusMonths(2));
        event(EventStatus.CANCELLED, FROM.plusMonths(3));
        event(EventStatus.PENDING, FROM.plusMonths(4));
        event(EventStatus.COMPLETED, FROM.minusDays(1));
        register(completed, true);
        register(completed, true);
        register(completed, false);

        String path = "/api/events/internal/clubs/{clubId}/statistics";
        mockMvc.perform(get(path, clubId).param("from", FROM.toString()).param("to", TO.toString())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.service("club-service"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.completedEvents").value(1))
                .andExpect(jsonPath("$.cancelledEvents").value(1))
                .andExpect(jsonPath("$.pendingEvents").value(1))
                .andExpect(jsonPath("$.registrations").value(3))
                .andExpect(jsonPath("$.attendances").value(2));
        mockMvc.perform(get(path, clubId).param("from", FROM.toString()).param("to", TO.toString())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
    }

    private UUID event(EventStatus status, LocalDateTime at) {
        Event event = new Event();
        event.setTitle("İstatistik etkinliği");
        event.setStartsAt(at);
        event.setEndsAt(event.getStartsAt().plusHours(2));
        event.setClubId(clubId);
        event.setClubName("İstatistik Kulübü");
        event.setStatus(status);
        return eventRepository.save(event).getId();
    }

    private void register(UUID eventId, boolean attended) {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(eventId);
        registration.setStudentId(UUID.randomUUID());
        registration.setQrCode(UUID.randomUUID().toString());
        registration.setAttended(attended);
        registrationRepository.save(registration);
    }
}
