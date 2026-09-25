package com.educonnect.notificationservice.listener;

import com.educonnect.common.security.LogMasking;
import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.EventRegistrationMessage;
import com.educonnect.notificationservice.service.EmailService;
import com.educonnect.notificationservice.service.QrCodeRenderer;
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
public class RegistrationNotificationListener {

    // 1. Manuel Logger Tanımı (Lombok @Slf4j yerine)
    private static final Logger log = LoggerFactory.getLogger(RegistrationNotificationListener.class);

    private final EmailService emailService;
    private final RestTemplate restTemplate;
    private final QrCodeRenderer qrCodeRenderer;

    // 2. Manuel Constructor (Lombok @RequiredArgsConstructor yerine)
    static final String QR_CONTENT_ID = "ticket-qr";

    public RegistrationNotificationListener(EmailService emailService, RestTemplate restTemplate,
                                            QrCodeRenderer qrCodeRenderer) {
        this.qrCodeRenderer = qrCodeRenderer;
        this.emailService = emailService;
        this.restTemplate = restTemplate;
    }

    /**
     * RabbitMQ'dan gelen kayıt mesajını dinler.
     * Kuyruk adı: NotificationRabbitMQConfig.NOTIFICATION_REGISTRATION_QUEUE
     */
    @RabbitListener(queues = NotificationRabbitMQConfig.NOTIFICATION_REGISTRATION_QUEUE)
    public void handleRegistration(EventRegistrationMessage message) {

        // DTO'dan verileri alıyoruz (Tip dönüşüm hatası olmaz)
        UUID studentId = message.getStudentId();
        String eventTitle = message.getEventTitle();
        String eventTime = message.getEventTime().toString(); // LocalDateTime'ı String'e çeviriyoruz
        String location = message.getLocation();
        String qrCode = message.getQrCode();

        log.info("Processing registration email for event: {}", eventTitle);

        // auth-services'ten öğrencinin e-posta adresini bulmak için URL
        // (Not: Servis adını büyük harfle AUTH-SERVICES olarak kullanıyoruz, LoadBalanced RestTemplate bunu çözer)
        String authServiceUrl = "http://AUTH-SERVICES/api/auth/internal/users/emails";

        // İstek gövdesi olarak ID listesi hazırlıyoruz
        List<UUID> ids = List.of(studentId);

        // RestTemplate ile POST isteği atıyoruz.
        // ParameterizedTypeReference kullanarak dönen cevabın List<String> olduğunu garanti ediyoruz.
        ResponseEntity<List<String>> response = restTemplate.exchange(
                authServiceUrl,
                HttpMethod.POST,
                new HttpEntity<>(ids),
                new ParameterizedTypeReference<List<String>>() {}
        );

        List<String> emails = response.getBody();

        // E-posta bulunduysa işlemi yap
        if (emails != null && !emails.isEmpty()) {
            String studentEmail = emails.get(0);
            String qrImageUrl = "cid:" + QR_CONTENT_ID;

            // HTML Mail İçeriğini Hazırlama
            String htmlBody = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <div style="background-color: #f4f4f4; padding: 20px; text-align: center;">
                        <h2 style="color: #2c3e50;">Tebrikler! Kaydınız Alındı.</h2>
                        <p><strong>%s</strong> etkinliğine başarıyla kaydoldunuz.</p>
                        
                        <div style="background-color: white; padding: 20px; border-radius: 8px; display: inline-block; margin-top: 10px;">
                            <p style="margin: 5px 0;">📅 <strong>Zaman:</strong> %s</p>
                            <p style="margin: 5px 0;">📍 <strong>Konum:</strong> %s</p>
                            <hr style="border: 0; border-top: 1px solid #eee; margin: 15px 0;">
                            <p>Giriş için aşağıdaki QR kodu görevliye gösteriniz:</p>
                            <img src="%s" alt="Bilet QR Kodu" style="border: 2px solid #333; padding: 5px; border-radius: 4px;"/>
                            <p style="font-size: 12px; color: #777; margin-top: 10px;">Bilet Kodu: %s</p>
                        </div>
                    </div>
                </body>
                </html>
                """, HtmlText.escape(eventTitle), HtmlText.escape(eventTime), HtmlText.escape(location), qrImageUrl, HtmlText.escape(qrCode));

            // Maili Gönder
            emailService.sendHtmlEmailWithInlineImage(studentEmail, "Biletiniz: " + eventTitle, htmlBody,
                    QR_CONTENT_ID, qrCodeRenderer.renderPng(qrCode), "image/png");

            log.info("Registration email sent to: {}", LogMasking.email(studentEmail));
        } else {
            log.warn("No email found for student ID: {}", studentId);
        }
    }
}