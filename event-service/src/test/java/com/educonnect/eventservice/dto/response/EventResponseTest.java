package com.educonnect.eventservice.dto.response;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventResponseTest {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Test
    void serializesTheFieldsTheFrontendReadsWithoutCreatorId() throws Exception {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setTitle("Satranç Turnuvası");
        event.setDescription("Açık turnuva");
        event.setEventTime(LocalDateTime.of(2026, 10, 5, 18, 30));
        event.setLocation("Konferans Salonu");
        event.setClubId(UUID.randomUUID());
        event.setClubName("Satranç Kulübü");
        event.setStatus(EventStatus.ACTIVE);
        event.setCreatedByStudentId(UUID.randomUUID());

        Map<String, Object> json = objectMapper.readValue(objectMapper.writeValueAsString(EventResponse.from(event)), JSON_MAP);

        assertThat(json.keySet()).containsExactlyInAnyOrder("id", "title", "description", "eventTime", "location",
                "imageUrl", "clubId", "clubName", "status", "createdAt", "updatedAt");
        assertThat(json.get("eventTime")).isEqualTo("2026-10-05T18:30:00");
        assertThat(json.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void pageResponseMapsContentAndKeepsPaging() {
        PageResponse<String> page = new PageResponse<>(List.of("a", "bb"), 1, 2, 7, 4, false, false);

        PageResponse<Integer> mapped = page.map(String::length);

        assertThat(mapped.content()).containsExactly(1, 2);
        assertThat(mapped).extracting(PageResponse::number, PageResponse::size, PageResponse::totalElements,
                PageResponse::totalPages).containsExactly(1, 2, 7L, 4);
    }
}
