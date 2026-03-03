package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.CourseNotificationMessage;
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
import java.util.UUID;

@Component
public class CourseNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationListener.class);

    private final EmailService emailService;
    private final RestTemplate restTemplate;

    public CourseNotificationListener(EmailService emailService, RestTemplate restTemplate) {
        this.emailService = emailService;
        this.restTemplate = restTemplate;
    }

    /**
     * Ders duyurusu oluşturulduğunda tetiklenir.
     * Kayıtlı öğrencilere toplu e-posta gönderir.
     */
    @RabbitListener(queues = NotificationRabbitMQConfig.COURSE_ANNOUNCEMENT_QUEUE)
    public void handleAnnouncementCreated(CourseNotificationMessage message) {
        log.info("📢 Ders duyurusu bildirimi alındı: {} -> Ders: {} ({})",
                message.getContentTitle(), message.getCourseTitle(), message.getCourseCode());

        sendBulkEmail(message, "Yeni Duyuru");
    }

    /**
     * Ödev oluşturulduğunda tetiklenir.
     * Kayıtlı öğrencilere toplu e-posta gönderir.
     */
    @RabbitListener(queues = NotificationRabbitMQConfig.COURSE_ASSIGNMENT_QUEUE)
    public void handleAssignmentCreated(CourseNotificationMessage message) {
        log.info("📝 Ödev bildirimi alındı: {} -> Ders: {} ({})",
                message.getContentTitle(), message.getCourseTitle(), message.getCourseCode());

        sendBulkEmail(message, "Yeni Ödev");
    }

    /**
     * Kayıtlı öğrencilere toplu e-posta gönderir.
     * 1. Mesajdaki enrolledStudentIds listesini kullanarak auth-services'ten e-postaları çeker
     * 2. Her öğrenciye e-posta gönderir
     */
    private void sendBulkEmail(CourseNotificationMessage message, String typeLabel) {
        List<UUID> studentIds = message.getEnrolledStudentIds();

        if (studentIds == null || studentIds.isEmpty()) {
            log.warn("⚠️ Kayıtlı öğrenci listesi boş. E-posta gönderilmedi.");
            return;
        }

        try {
            // auth-services'ten öğrenci e-postalarını çek
            String authServiceUrl = "http://AUTH-SERVICES/api/auth/users/emails";
            log.info("🔍 {} öğrenci için e-posta adresleri çekiliyor...", studentIds.size());

            HttpEntity<List<UUID>> request = new HttpEntity<>(studentIds);
            ResponseEntity<List<String>> emailsResponse = restTemplate.exchange(
                    authServiceUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            List<String> emails = emailsResponse.getBody();

            log.info("📧 {} e-posta adresi alındı.", emails != null ? emails.size() : 0);

            if (emails != null && !emails.isEmpty()) {
                String subject = String.format("[%s] %s: %s",
                        message.getCourseCode(), typeLabel, message.getContentTitle());

                String body = buildEmailBody(message, typeLabel);

                for (String email : emails) {
                    emailService.sendSimpleEmail(email, subject, body);
                }

                log.info("✅ {} öğrenciye '{}' e-postası gönderildi. Ders: {} ({})",
                        emails.size(), typeLabel, message.getCourseTitle(), message.getCourseCode());
            } else {
                log.warn("⚠️ Öğrenci e-postaları bulunamadı.");
            }

        } catch (Exception e) {
            log.error("❌ Toplu e-posta gönderme hatası: {}", e.getMessage(), e);
        }
    }

    /**
     * E-posta içeriğini oluşturur.
     */
    private String buildEmailBody(CourseNotificationMessage message, String typeLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("Merhaba,\n\n");
        sb.append(String.format("%s dersinde (%s) yeni bir %s paylaşıldı.\n\n",
                message.getCourseTitle(), message.getCourseCode(),
                typeLabel.toLowerCase()));
        sb.append("Başlık: ").append(message.getContentTitle()).append("\n");

        if (message.getContentDescription() != null && !message.getContentDescription().isEmpty()) {
            sb.append("\nİçerik:\n").append(message.getContentDescription()).append("\n");
        }

        sb.append("\nDetaylar için EduConnect platformunu ziyaret ediniz.\n");
        sb.append("\nİyi çalışmalar,\nEduConnect Ekibi");
        return sb.toString();
    }
}

