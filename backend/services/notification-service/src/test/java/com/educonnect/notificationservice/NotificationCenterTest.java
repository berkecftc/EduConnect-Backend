package com.educonnect.notificationservice;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.test.TestTokens;
import com.educonnect.notificationservice.client.ContactDirectory;
import com.educonnect.notificationservice.model.EmailStatus;
import com.educonnect.notificationservice.model.Notification;
import com.educonnect.notificationservice.repository.NotificationRepository;
import com.educonnect.notificationservice.service.NotificationDispatcher;
import com.educonnect.notificationservice.service.UnsubscribeTokens;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@NotificationIntegrationTest
class NotificationCenterTest {

    private final UUID active = UUID.randomUUID();
    private final UUID suspended = UUID.randomUUID();
    private final UUID unknown = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private UnsubscribeTokens tokens;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private ContactDirectory contacts;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void stubs() {
        when(contacts.lookup(any())).thenReturn(Map.of(
                active, new ContactDirectory.Contact(active, "ayse@ogr.uni.edu.tr", true),
                suspended, new ContactDirectory.Contact(suspended, "askida@ogr.uni.edu.tr", false)));
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void serviceNotificationsReachEveryInboxButEmailOnlyActiveAccounts() throws Exception {
        int stored = dispatcher.dispatch(NotificationRequest.of(List.of(active, suspended, unknown), NotificationCategory.COURSE,
                "COURSE_ANNOUNCEMENT", "[BIL101] Yeni Duyuru: Vize", "Vize 3 Kasım'da.", "/courses/abc", null));

        assertThat(stored).isEqualTo(3);
        assertThat(statusOf(active)).isEqualTo(EmailStatus.SENT);
        assertThat(statusOf(suspended)).isEqualTo(EmailStatus.SKIPPED);
        assertThat(statusOf(unknown)).isEqualTo(EmailStatus.SKIPPED);
        MimeMessage sent = lastMail();
        assertThat(sent.getSubject()).isEqualTo("[BIL101] Yeni Duyuru: Vize");
        assertThat(sent.getHeader("List-Unsubscribe")).isNull();
        assertThat(String.valueOf(sent.getContent())).isNotNull();
    }

    @Test
    void optionalCategoriesFollowPreferencesAndCarryAnUnsubscribeLink() throws Exception {
        dispatcher.dispatch(NotificationRequest.of(List.of(active), NotificationCategory.COMMUNITY, "COMMENT",
                "Gönderinize yorum geldi", "Bir yanıt var.", "/posts/1", null));
        assertThat(statusOf(active)).isEqualTo(EmailStatus.SKIPPED);
        verify(mailSender, never()).send(any(MimeMessage.class));

        mockMvc.perform(as(put("/api/notifications/preferences"), active).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"COMMUNITY\",\"emailEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true));
        dispatcher.dispatch(NotificationRequest.of(List.of(active), NotificationCategory.COMMUNITY, "COMMENT",
                "Gönderinize yorum geldi", "İkinci yanıt.", "/posts/1", null));

        MimeMessage sent = lastMail();
        assertThat(sent.getHeader("List-Unsubscribe")[0]).contains("/api/notifications/unsubscribe?token=");
        assertThat(sent.getHeader("List-Unsubscribe-Post")[0]).isEqualTo("List-Unsubscribe=One-Click");

        String token = tokens.issue(active, NotificationCategory.COMMUNITY);
        mockMvc.perform(post("/api/notifications/unsubscribe").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("COMMUNITY"))
                .andExpect(jsonPath("$.emailEnabled").value(false));
        mockMvc.perform(post("/api/notifications/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.substring(0, token.length() - 2) + "xx\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_UNSUBSCRIBE_TOKEN"));
        mockMvc.perform(as(get("/api/notifications/preferences"), active))
                .andExpect(jsonPath("$[?(@.category == 'COMMUNITY')].emailEnabled").value(false))
                .andExpect(jsonPath("$[?(@.category == 'COURSE')].mandatory").value(true));
        mockMvc.perform(as(put("/api/notifications/preferences"), active).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"COURSE\",\"emailEnabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_MANDATORY"));
    }

    @Test
    void usersReadAndClearTheirOwnInboxOnly() throws Exception {
        UUID reader = UUID.randomUUID();
        when(contacts.lookup(any())).thenReturn(Map.of());
        dispatcher.dispatch(NotificationRequest.of(List.of(reader), NotificationCategory.EVENT, "EVENT_TICKET", "Biletiniz: A", "a", null, null));
        dispatcher.dispatch(NotificationRequest.of(List.of(reader), NotificationCategory.EVENT, "EVENT_TICKET", "Biletiniz: B", "b", null, null));
        UUID first = repository.findByRecipientIdOrderByCreatedAtDesc(reader, Pageable.unpaged()).getContent().getLast().getId();

        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/notifications/unread-count"), reader)).andExpect(jsonPath("$.unread").value(2));
        mockMvc.perform(as(post("/api/notifications/{id}/read", first), UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(as(post("/api/notifications/{id}/read", first), reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(as(get("/api/notifications").param("unreadOnly", "true"), reader))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Biletiniz: B"));
        mockMvc.perform(as(post("/api/notifications/read-all"), reader)).andExpect(jsonPath("$.updated").value(1));
        mockMvc.perform(as(get("/api/notifications"), reader))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].read").value(true));
    }

    @Test
    void duplicateRequestsAreStoredOnceAndQueuedRequestsAreDelivered() throws Exception {
        UUID recipient = UUID.randomUUID();
        NotificationRequest request = NotificationRequest.of(List.of(recipient), NotificationCategory.CLUB_NEWS,
                "EVENT_ANNOUNCED", "Yeni etkinlik", "Satranç turnuvası", "/events/1", "event-announced:1");
        assertThat(dispatcher.dispatch(request)).isEqualTo(1);
        assertThat(dispatcher.dispatch(request)).isZero();

        UUID queued = UUID.randomUUID();
        rabbitTemplate.convertAndSend(NotificationRequest.EXCHANGE, NotificationRequest.ROUTING_KEY,
                NotificationRequest.of(List.of(queued), NotificationCategory.MODERATION, "POST_REJECTED", "Gönderiniz reddedildi",
                        "Gerekçe: hakaret", "/posts/2", null));
        for (int i = 0; i < 50 && repository.countByRecipientIdAndReadAtIsNull(queued) == 0; i++) {
            Thread.sleep(200);
        }
        assertThat(repository.countByRecipientIdAndReadAtIsNull(queued)).isEqualTo(1);
    }

    private EmailStatus statusOf(UUID recipient) {
        List<Notification> rows = repository.findByRecipientIdOrderByCreatedAtDesc(recipient, Pageable.unpaged()).getContent();
        return rows.getFirst().getEmailStatus();
    }

    private MimeMessage lastMail() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        MimeMessage message = captor.getValue();
        clearInvocations(mailSender);
        return message;
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID userId) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(userId)));
    }
}
