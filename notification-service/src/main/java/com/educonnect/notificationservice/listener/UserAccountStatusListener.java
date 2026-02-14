package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.UserAccountStatusMessage;
import com.educonnect.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Kullanıcı hesap durumu (onay/red) mesajlarını dinler ve e-posta gönderir.
 */
@Component
public class UserAccountStatusListener {

    private static final Logger log = LoggerFactory.getLogger(UserAccountStatusListener.class);

    private final EmailService emailService;

    public UserAccountStatusListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.USER_ACCOUNT_STATUS_QUEUE)
    public void handleUserAccountStatus(UserAccountStatusMessage message) {
        log.info("Kullanıcı hesap durumu mesajı alındı: email={}, status={}, userType={}",
                message.getEmail(), message.getStatus(), message.getUserType());

        try {
            String subject;
            String htmlBody;

            if ("APPROVED".equals(message.getStatus())) {
                subject = "EduConnect - Hesabınız Onaylandı!";
                htmlBody = buildApprovalEmail(message);
            } else if ("REJECTED".equals(message.getStatus())) {
                subject = "EduConnect - Başvurunuz Hakkında Bilgilendirme";
                htmlBody = buildRejectionEmail(message);
            } else {
                log.warn("Bilinmeyen durum: {}", message.getStatus());
                return;
            }

            emailService.sendHtmlEmail(message.getEmail(), subject, htmlBody);
            log.info("Hesap durumu e-postası gönderildi: {}", message.getEmail());

        } catch (Exception e) {
            log.error("Hesap durumu e-postası gönderilemedi: {}", e.getMessage(), e);
        }
    }

    private String buildApprovalEmail(UserAccountStatusMessage message) {
        String userTypeText = "STUDENT".equals(message.getUserType()) ? "Öğrenci" : "Akademisyen";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; color: #333; background-color: #f9f9f9; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <h1 style="color: #27ae60; margin: 0;">🎉 Hoş Geldiniz!</h1>
                    </div>
                    
                    <p style="font-size: 16px;">Sayın <strong>%s %s</strong>,</p>
                    
                    <p style="font-size: 16px;">
                        %s hesap başvurunuz <span style="color: #27ae60; font-weight: bold;">onaylanmıştır</span>.
                    </p>
                    
                    <p style="font-size: 16px;">
                        Artık EduConnect platformuna giriş yapabilir ve tüm özelliklerden yararlanabilirsiniz.
                    </p>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="https://educonnect.com/login" 
                           style="background-color: #3498db; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-size: 16px;">
                            Giriş Yap
                        </a>
                    </div>
                    
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    
                    <p style="font-size: 14px; color: #888; text-align: center;">
                        EduConnect Ekibi<br>
                        <small>Bu e-posta otomatik olarak gönderilmiştir.</small>
                    </p>
                </div>
            </body>
            </html>
            """,
            message.getFirstName(),
            message.getLastName(),
            userTypeText
        );
    }

    private String buildRejectionEmail(UserAccountStatusMessage message) {
        String userTypeText = "STUDENT".equals(message.getUserType()) ? "Öğrenci" : "Akademisyen";
        String reasonSection = "";

        if (message.getRejectionReason() != null && !message.getRejectionReason().isEmpty()) {
            reasonSection = String.format("""
                <div style="background-color: #fff3cd; border: 1px solid #ffc107; border-radius: 5px; padding: 15px; margin: 20px 0;">
                    <strong>Red Nedeni:</strong><br>
                    %s
                </div>
                """, message.getRejectionReason());
        }

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; color: #333; background-color: #f9f9f9; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <h1 style="color: #e74c3c; margin: 0;">Başvuru Sonucu</h1>
                    </div>
                    
                    <p style="font-size: 16px;">Sayın <strong>%s %s</strong>,</p>
                    
                    <p style="font-size: 16px;">
                        %s hesap başvurunuz değerlendirilmiş ve maalesef <span style="color: #e74c3c; font-weight: bold;">reddedilmiştir</span>.
                    </p>
                    
                    %s
                    
                    <p style="font-size: 16px;">
                        Başvurunuzla ilgili sorularınız için destek ekibimizle iletişime geçebilirsiniz.
                    </p>
                    
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    
                    <p style="font-size: 14px; color: #888; text-align: center;">
                        EduConnect Ekibi<br>
                        <small>Bu e-posta otomatik olarak gönderilmiştir.</small>
                    </p>
                </div>
            </body>
            </html>
            """,
            message.getFirstName(),
            message.getLastName(),
            userTypeText,
            reasonSection
        );
    }
}

