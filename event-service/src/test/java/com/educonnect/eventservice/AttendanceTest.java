package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.CheckInMethod;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class AttendanceTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID ayse = UUID.randomUUID();
    private final UUID burak = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventRegistrationRepository registrationRepository;

    @Autowired
    private ClubClient clubClient;

    @BeforeEach
    void grants() {
        given(clubClient.getAccess(any(), any())).willAnswer(call -> {
            UUID user = call.getArgument(1);
            Set<String> permissions = user.equals(officer) ? Set.of("MANAGE_EVENT_OPERATIONS")
                    : user.equals(advisor) ? Set.of("ADVISE") : Set.of();
            return new ClubAccess(clubId, user, null, true, false, user.equals(advisor), permissions);
        });
    }

    @Test
    void organisersCheckPeopleInByHandOrQrAndSeeWhoCameWhenAndHow() throws Exception {
        Event live = event(LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusHours(1));
        register(live, ayse);
        String qr = register(live, burak);
        String checkIn = "/api/events/manage/{event}/registrations/{student}/check-in";

        mockMvc.perform(as(post(checkIn, live.getId(), ayse), TestTokens.student(member))).andExpect(status().isForbidden());
        mockMvc.perform(as(post(checkIn, live.getId(), ayse), TestTokens.student(officer))).andExpect(status().isNoContent());
        mockMvc.perform(as(post(checkIn, live.getId(), ayse), TestTokens.student(officer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_CHECKED_IN"));
        mockMvc.perform(as(delete(checkIn, live.getId(), ayse), TestTokens.student(officer))).andExpect(status().isNoContent());
        mockMvc.perform(as(post(checkIn, live.getId(), ayse), TestTokens.student(officer))).andExpect(status().isNoContent());
        mockMvc.perform(as(post("/api/events/manage/verify-qr"), TestTokens.student(officer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrCode\":\"" + qr + "\"}"))
                .andExpect(status().isOk());

        EventRegistration manual = registrationRepository.findByEventIdAndStudentId(live.getId(), ayse).orElseThrow();
        assertThat(manual.getCheckInMethod()).isEqualTo(CheckInMethod.MANUAL);
        assertThat(manual.getCheckedInBy()).isEqualTo(officer);
        assertThat(registrationRepository.findByEventIdAndStudentId(live.getId(), burak).orElseThrow().getCheckInMethod())
                .isEqualTo(CheckInMethod.QR);

        mockMvc.perform(as(get("/api/events/manage/{id}/attendance", live.getId()), TestTokens.academician(advisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(2))
                .andExpect(jsonPath("$.attended").value(2))
                .andExpect(jsonPath("$.rows.length()").value(2));
        mockMvc.perform(as(get("/api/events/manage/{id}/attendance", live.getId()), TestTokens.student(member)))
                .andExpect(status().isForbidden());
        byte[] csv = mockMvc.perform(as(get("/api/events/manage/{id}/attendance.csv", live.getId()), TestTokens.student(officer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Ad;Soyad;Öğrenci No;Kayıt Durumu;Katıldı;Giriş Zamanı;Giriş Yöntemi");
        assertThat(text).contains(";REGISTERED;Evet;").contains("MANUAL").contains("QR");
    }

    @Test
    void checkInOutsideTheWindowIsRefusedAndClubsGetAPerEventSummary() throws Exception {
        Event upcoming = event(LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2));
        register(upcoming, ayse);
        mockMvc.perform(as(post("/api/events/manage/{event}/registrations/{student}/check-in", upcoming.getId(), ayse),
                        TestTokens.student(officer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CHECK_IN_CLOSED"));

        String from = LocalDate.now().minusDays(1).toString();
        String to = LocalDate.now().plusDays(5).toString();
        mockMvc.perform(as(get("/api/events/manage/club/{club}/attendance", clubId).param("from", from).param("to", to),
                        TestTokens.student(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventId == '" + upcoming.getId() + "')].registered").value(1));
        mockMvc.perform(as(get("/api/events/manage/club/{club}/attendance", clubId).param("from", from).param("to", to),
                        TestTokens.student(member)))
                .andExpect(status().isForbidden());
    }

    private String register(Event event, UUID student) {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(event.getId());
        registration.setStudentId(student);
        registration.setQrCode(UUID.randomUUID().toString());
        return registrationRepository.save(registration).getQrCode();
    }

    private Event event(LocalDateTime startsAt, LocalDateTime endsAt) {
        Event event = new Event();
        event.setTitle("Yoklamalı Etkinlik");
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        event.setClubId(clubId);
        event.setClubName("Kulüp");
        event.setStatus(EventStatus.ACTIVE);
        return eventRepository.save(event);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
