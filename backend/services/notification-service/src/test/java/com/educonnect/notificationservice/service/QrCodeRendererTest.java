package com.educonnect.notificationservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrCodeRendererTest {

    @Test
    void renderPng_shouldProducePngLocally() {
        byte[] png = new QrCodeRenderer().renderPng("TICKET-123e4567-e89b-12d3-a456-426614174000");

        assertThat(png).isNotEmpty();
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(new String(png, 1, 3)).isEqualTo("PNG");
    }

    @Test
    void renderPng_shouldRejectEmptyContent() {
        assertThatThrownBy(() -> new QrCodeRenderer().renderPng(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
