package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.EventCreatedMessage; // YENİ DTO
import com.educonnect.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.ParameterizedTypeReference; // YENİ
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Component
public class EventNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EventNotificationListener.class);

    private final EmailService emailService;
    private final RestTemplate restTemplate;

    public EventNotificationListener(EmailService emailService, RestTemplate restTemplate) {
        this.emailService = emailService;
        this.restTemplate = restTemplate;
    }

    // DİKKAT: Parametre artık Map değil, EventCreatedMessage
    @RabbitListener(queues = NotificationRabbitMQConfig.NOTIFICATION_EVENT_QUEUE)
    public void handleEventCreated(EventCreatedMessage message) {

        // Verileri güvenli bir şekilde DTO'dan alıyoruz
        String eventTitle = message.getTitle();
        String clubName = message.getClubName();
        UUID clubId = message.getClubId();
        String eventTime = message.getEventTime().toString();

        log.info("📢 Handling event notification for: {} | Club: {} | ClubId: {}", eventTitle, clubName, clubId);

        try {
            // 1. ADIM: club-service'ten üye ID'lerini çek
            String clubServiceUrl = "http://CLUB-SERVICE/api/clubs/internal/" + clubId + "/members/ids";
            log.info("🔍 Fetching member IDs from: {}", clubServiceUrl);

            ResponseEntity<List<UUID>> memberIdsResponse = restTemplate.exchange(
                    clubServiceUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<UUID>>() {}
            );
            List<UUID> memberIds = memberIdsResponse.getBody();

            log.info("👥 Member IDs received: {}", memberIds);

            if (memberIds == null || memberIds.isEmpty()) {
                log.warn("⚠️ No members found for club '{}' (ID: {}). Skipping emails.", clubName, clubId);
                return;
            }

            // 2. ADIM: auth-services'ten bu ID'lerin e-postalarını çek
            String authServiceUrl = "http://AUTH-SERVICES/api/auth/internal/users/emails";
            log.info("🔍 Fetching emails from auth-services for {} member(s)", memberIds.size());

            HttpEntity<List<UUID>> request = new HttpEntity<>(memberIds);
            ResponseEntity<List<String>> emailsResponse = restTemplate.exchange(
                    authServiceUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            List<String> emails = emailsResponse.getBody();

            log.info("📧 {} e-posta adresi alındı.", emails != null ? emails.size() : 0);

            // 3. ADIM: Herkese mail gönder
            if (emails != null && !emails.isEmpty()) {
                for (String email : emails) {
                    String subject = "Yeni Etkinlik: " + eventTitle;
                    String body = String.format("Merhaba,\n\n%s kulübü '%s' etkinliğini duyurdu!\nZaman: %s\n\nKaçırma!", clubName, eventTitle, eventTime);

                    emailService.sendSimpleEmail(email, subject, body);
                }
                log.info("✅ Sent notifications to {} members.", emails.size());
            } else {
                log.warn("⚠️ No emails found for the member IDs. Check auth-services.");
            }

        } catch (Exception e) {
            log.error("❌ Failed to send notifications: {}", e.getMessage(), e);
        }
    }
}