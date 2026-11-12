package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.repository.EventRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EventIntegrationTest
class CampusEventTest {

    private final UUID publisher = UUID.randomUUID();
    private final String publisherToken = TestTokens.user(publisher, "ROLE_STAFF,PERM_CAMPUS_PUBLISHER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void campusPublishersPublishEventsOpenToTheWholeCampus() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(4).withNano(0);
        mockMvc.perform(as(multipart("/api/events/campus").file(data(start, "Kariyer Merkezi")), TestTokens.student(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(multipart("/api/events/campus").file(data(start, "")), publisherToken))
                .andExpect(status().isBadRequest());
        String id = JsonPath.read(mockMvc.perform(as(multipart("/api/events/campus").file(data(start, "Kariyer Merkezi")), publisherToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.audience").value("CAMPUS"))
                .andExpect(jsonPath("$.admission").value("AUTO_CONFIRM"))
                .andExpect(jsonPath("$.organizerName").value("Kariyer Merkezi"))
                .andExpect(jsonPath("$.clubId").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/events")).andExpect(jsonPath("$[*].id", hasItem(id)));
        mockMvc.perform(as(get("/api/events/campus"), TestTokens.admin(UUID.randomUUID())))
                .andExpect(jsonPath("$[*].id", hasItem(id)));

        UUID teacher = UUID.randomUUID();
        mockMvc.perform(as(post("/api/events/{id}/participation-request", id), TestTokens.academician(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(as(get("/api/events/manage/{id}/registrations", id), publisherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(teacher.toString()));
        mockMvc.perform(as(get("/api/events/manage/{id}/registrations", id), TestTokens.student(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        String later = start.plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        mockMvc.perform(as(post("/api/events/manage/{id}/postpone", id), publisherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\":\"" + later + "\",\"reason\":\"Salon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(as(post("/api/events/manage/{id}/cancel", id), TestTokens.student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/events/manage/{id}/cancel", id), publisherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Hava muhalefeti\"}"))
                .andExpect(status().isOk());
        assertThat(eventRepository.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    private static MockMultipartFile data(LocalDateTime start, String organizer) {
        String json = "{\"title\":\"Kariyer Günleri\",\"startsAt\":\"" + start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "\",\"location\":\"Kongre Merkezi\",\"organizerName\":\"" + organizer + "\",\"capacity\":200}";
        return new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
