package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.ClubMembershipNotificationMessage;
import com.educonnect.notificationservice.dto.message.ClubRoleChangeNotificationMessage;
import com.educonnect.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ClubNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ClubNotificationListener.class);
    private static final String AUTH_EMAILS_URL = "http://AUTH-SERVICES/api/auth/users/emails";

    private final EmailService emailService;
    private final RestTemplate restTemplate;

    public ClubNotificationListener(EmailService emailService, RestTemplate restTemplate) {
        this.emailService = emailService;
        this.restTemplate = restTemplate;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.CLUB_MEMBERSHIP_NOTIFICATION_QUEUE)
    public void handleMembershipNotification(ClubMembershipNotificationMessage message) {
        log.info("Club membership notification received: clubId={}, status={}", message.clubId(), message.status());

        findEmail(message.studentId()).ifPresent(email -> {
            String subject = "APPROVED".equals(message.status())
                    ? "EduConnect - " + message.clubName() + " üyeliğiniz onaylandı"
                    : "EduConnect - " + message.clubName() + " üyelik başvurunuz hakkında";
            emailService.sendSimpleEmail(email, subject, "Merhaba,\n\n" + message.message() + "\n\nEduConnect");
        });
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.CLUB_ROLE_CHANGE_NOTIFICATION_QUEUE)
    public void handleRoleChangeNotification(ClubRoleChangeNotificationMessage message) {
        log.info("Club role change notification received: clubId={}, type={}, status={}",
                message.clubId(), message.notificationType(), message.status());

        findEmail(message.targetUserId()).ifPresent(email -> {
            String subject = "EduConnect - " + message.clubName() + " görev değişikliği";
            emailService.sendSimpleEmail(email, subject, "Merhaba,\n\n" + message.message() + "\n\nEduConnect");
        });
    }

    private Optional<String> findEmail(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    AUTH_EMAILS_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(List.of(userId)),
                    new ParameterizedTypeReference<List<String>>() {}
            );
            List<String> emails = response.getBody();
            if (emails == null || emails.isEmpty()) {
                log.warn("No email found for userId={}", userId);
                return Optional.empty();
            }
            return Optional.of(emails.get(0));
        } catch (Exception e) {
            log.error("Email lookup failed for userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
