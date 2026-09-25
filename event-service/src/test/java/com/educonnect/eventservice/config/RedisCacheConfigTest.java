package com.educonnect.eventservice.config;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisCacheConfigTest {

    private final GenericJackson2JsonRedisSerializer serializer = RedisCacheConfig.cacheValueSerializer();

    @Test
    void cacheValueSerializer_withApplicationTypes_shouldRoundTrip() {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setTitle("Tanışma");
        event.setEventTime(LocalDateTime.of(2026, 10, 1, 18, 0));
        event.setStatus(EventStatus.ACTIVE);
        List<Event> events = new ArrayList<>(List.of(event));

        Object restored = serializer.deserialize(serializer.serialize(events));

        assertThat(restored).isInstanceOf(List.class);
        Event restoredEvent = (Event) ((List<?>) restored).get(0);
        assertThat(restoredEvent.getId()).isEqualTo(event.getId());
        assertThat(restoredEvent.getEventTime()).isEqualTo(event.getEventTime());
        assertThat(restoredEvent.getStatus()).isEqualTo(EventStatus.ACTIVE);
    }

    @Test
    void cacheValueSerializer_withForeignType_shouldReject() {
        byte[] payload = "[\"java.util.ArrayList\",[{\"@class\":\"org.springframework.context.support.ClassPathXmlApplicationContext\",\"configLocation\":\"http://example.invalid/x.xml\"}]]"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(payload))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("ClassPathXmlApplicationContext");
    }
}
