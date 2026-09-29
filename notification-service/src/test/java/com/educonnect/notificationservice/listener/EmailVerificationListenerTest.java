package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.dto.message.EmailVerificationMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationListenerTest {

    @Test
    void buildEmail_shouldEscapeUserProvidedName() {
        String html = EmailVerificationListener.buildEmail(new EmailVerificationMessage(
                "ayse@example.edu", "<script>alert(1)</script>", "https://api.example.edu/api/auth/verify-email?token=abc", 24));

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(html).contains("https://api.example.edu/api/auth/verify-email?token=abc");
        assertThat(html).contains("24 saat");
    }

    @Test
    void statusChangeEmail_shouldEscapeReason() {
        String html = UserAccountStatusListener.buildStatusChangeEmail("Başlık", "Metin", "<b>kötü</b>");

        assertThat(html).doesNotContain("<b>kötü</b>").contains("&lt;b&gt;kötü&lt;/b&gt;");
    }
}
