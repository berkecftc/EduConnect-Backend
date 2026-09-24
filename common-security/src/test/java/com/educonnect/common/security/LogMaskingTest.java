package com.educonnect.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingTest {

    @Test
    void email_shouldHideLocalPartButKeepDomain() {
        assertThat(LogMasking.email("ayse.yilmaz@example.edu")).isEqualTo("ay***@example.edu");
        assertThat(LogMasking.email("a@example.edu")).isEqualTo("a***@example.edu");
        assertThat(LogMasking.email("gecersiz")).isEqualTo("***");
        assertThat(LogMasking.email(null)).isEqualTo("-");
    }
}
