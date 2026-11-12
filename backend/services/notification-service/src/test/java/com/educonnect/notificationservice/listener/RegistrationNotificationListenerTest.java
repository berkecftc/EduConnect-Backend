package com.educonnect.notificationservice.listener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationNotificationListenerTest {

    @Test
    void ticketWordingFollowsHowTheSeatWasObtained() {
        assertThat(RegistrationNotificationListener.title(null)).isEqualTo("Biletiniz: ");
        assertThat(RegistrationNotificationListener.title("SELF")).isEqualTo("Biletiniz: ");
        assertThat(RegistrationNotificationListener.title("REQUEST_APPROVED")).isEqualTo("Katılım talebiniz onaylandı: ");
        assertThat(RegistrationNotificationListener.title("WAITLIST_PROMOTED")).isEqualTo("Bekleme listesinden kayda geçtiniz: ");
        assertThat(RegistrationNotificationListener.lead("WAITLIST_PROMOTED")).contains("bekleme listesinden");
        assertThat(RegistrationNotificationListener.lead(null)).isEqualTo("etkinliğine kaydınız alındı.");
    }
}
