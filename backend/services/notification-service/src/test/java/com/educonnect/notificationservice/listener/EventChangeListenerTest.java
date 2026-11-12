package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.dto.message.EventChangedMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventChangeListenerTest {

    @Test
    void participantsLearnWhatChangedAndWhy() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 3, 18, 30);
        EventChangedMessage postponed = message("POSTPONED", start, "Salon arızası");
        assertThat(EventChangeListener.subject(postponed)).isEqualTo("Etkinlik ertelendi: Atölye");
        assertThat(EventChangeListener.body(postponed)).contains("Yeni tarih: 3 Kasım 2026 18:30").contains("Gerekçe: Salon arızası");

        assertThat(EventChangeListener.body(message("RELOCATED", start, null))).contains("Yeni yer: B-204").doesNotContain("Gerekçe");
        assertThat(EventChangeListener.subject(message("CANCELLED", start, "Konuşmacı gelemiyor"))).startsWith("Etkinlik iptal edildi");
    }

    private static EventChangedMessage message(String kind, LocalDateTime start, String reason) {
        return new EventChangedMessage(UUID.randomUUID(), "Atölye", "Kulüp", kind, start, start.plusHours(2), "B-204", reason,
                List.of(UUID.randomUUID()));
    }
}
