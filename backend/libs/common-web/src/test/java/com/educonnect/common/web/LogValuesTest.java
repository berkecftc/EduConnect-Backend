package com.educonnect.common.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogValuesTest {

    @Test
    void lineBreaksCannotForgeLogLines() {
        assertThat(LogValues.safe("a\r\nb\nc\rd e")).isEqualTo("a_b_c_d_e");
        assertThat(LogValues.safe(List.of("x\ny"))).isEqualTo("[x_y]");
        assertThat(LogValues.safe("/api/clubs/1")).isEqualTo("/api/clubs/1");
        assertThat(LogValues.safe(null)).isNull();
    }
}
