package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.EventChangedMessage;
import com.educonnect.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class EventChangeListener {

    private static final Logger log = LoggerFactory.getLogger(EventChangeListener.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr"));
    private static final String EMAILS = "http://AUTH-SERVICES/api/auth/internal/users/emails";

    private final EmailService emailService;
    private final RestTemplate restTemplate;

    public EventChangeListener(EmailService emailService, RestTemplate restTemplate) {
        this.emailService = emailService;
        this.restTemplate = restTemplate;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.NOTIFICATION_EVENT_CHANGED_QUEUE)
    public void handleEventChanged(EventChangedMessage message) {
        if (message == null || message.kind() == null || message.recipientIds() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid event change message");
        }
        if (message.recipientIds().isEmpty()) {
            return;
        }
        List<String> emails = restTemplate.exchange(EMAILS, HttpMethod.POST, new HttpEntity<>(message.recipientIds()),
                new ParameterizedTypeReference<List<String>>() {
                }).getBody();
        if (emails == null || emails.isEmpty()) {
            log.warn("No emails found for event change recipients");
            return;
        }
        String subject = subject(message);
        String body = body(message);
        int failed = 0;
        for (String email : emails) {
            try {
                emailService.sendSimpleEmail(email, subject, body);
            } catch (RuntimeException e) {
                failed++;
                log.warn("Event change notification could not be sent: {}", e.getMessage());
            }
        }
        log.info("Event change ({}) notified {} of {} participants", message.kind(), emails.size() - failed, emails.size());
    }

    static String subject(EventChangedMessage message) {
        return switch (message.kind()) {
            case "POSTPONED" -> "Etkinlik ertelendi: " + message.title();
            case "RELOCATED" -> "Etkinlik yeri değişti: " + message.title();
            case "CANCELLED" -> "Etkinlik iptal edildi: " + message.title();
            default -> "Etkinlik güncellendi: " + message.title();
        };
    }

    static String body(EventChangedMessage message) {
        StringBuilder text = new StringBuilder("Merhaba,\n\nKayıtlı olduğunuz \"").append(message.title()).append("\" etkinliğinde değişiklik var.\n\n");
        switch (message.kind()) {
            case "POSTPONED" -> text.append("Yeni tarih: ").append(TIME.format(message.startsAt())).append(" – ")
                    .append(TIME.format(message.endsAt())).append("\nKaydınız geçerlidir; katılamayacaksanız kaydınızı iptal edebilirsiniz.\n");
            case "RELOCATED" -> text.append("Yeni yer: ").append(message.location()).append('\n');
            case "CANCELLED" -> text.append("Etkinlik iptal edildi.\n");
            default -> {
            }
        }
        if (message.reason() != null && !message.reason().isBlank()) {
            text.append("Gerekçe: ").append(message.reason()).append('\n');
        }
        return text.append("\nEduConnect").toString();
    }
}
