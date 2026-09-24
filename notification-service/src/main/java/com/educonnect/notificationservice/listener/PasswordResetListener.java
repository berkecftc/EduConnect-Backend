package com.educonnect.notificationservice.listener;

import com.educonnect.common.security.LogMasking;
import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.PasswordResetMessage;
import com.educonnect.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Şifre sıfırlama mesajlarını dinler ve e-posta gönderir.
 */
@Component
public class PasswordResetListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetListener.class);

    private final EmailService emailService;

    public PasswordResetListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.PASSWORD_RESET_QUEUE)
    public void handlePasswordReset(PasswordResetMessage message) {
        log.info("Şifre sıfırlama mesajı alındı: email={}", LogMasking.email(message.getEmail()));

        try {
            String subject = "EduConnect - Şifre Sıfırlama Talebi";
            String htmlBody = buildPasswordResetEmail(message);

            emailService.sendHtmlEmail(message.getEmail(), subject, htmlBody);
            log.info("Şifre sıfırlama e-postası gönderildi: {}", LogMasking.email(message.getEmail()));

        } catch (Exception e) {
            log.error("Şifre sıfırlama e-postası gönderilemedi: {}", e.getMessage(), e);
        }
    }

    private String buildPasswordResetEmail(PasswordResetMessage message) {
        // Kullanıcı adı varsa kullan, yoksa "Sayın Kullanıcı" yaz
        String greeting;
        if (message.getFirstName() != null && message.getLastName() != null) {
            greeting = String.format("Sayın <strong>%s %s</strong>,", message.getFirstName(), message.getLastName());
        } else {
            greeting = "Sayın Kullanıcı,";
        }

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; color: #333; background-color: #f9f9f9; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <h1 style="color: #3498db; margin: 0;">🔐 Şifre Sıfırlama</h1>
                    </div>
                    
                    <p style="font-size: 16px;">%s</p>
                    
                    <p style="font-size: 16px;">
                        EduConnect hesabınız için bir şifre sıfırlama talebi aldık.
                    </p>
                    
                    <p style="font-size: 16px;">
                        Şifrenizi sıfırlamak için aşağıdaki butona tıklayın:
                    </p>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" 
                           style="background-color: #3498db; color: white; padding: 14px 35px; text-decoration: none; border-radius: 5px; font-size: 16px; font-weight: bold;">
                            Şifremi Sıfırla
                        </a>
                    </div>
                    
                    <div style="background-color: #fff3cd; border: 1px solid #ffc107; border-radius: 5px; padding: 15px; margin: 20px 0;">
                        <strong>⚠️ Önemli:</strong><br>
                        Bu link <strong>15 dakika</strong> içinde geçerliliğini yitirecektir.<br>
                        Eğer bu talebi siz yapmadıysanız, bu e-postayı görmezden gelebilirsiniz.
                    </div>
                    
                    <p style="font-size: 14px; color: #666;">
                        Link çalışmıyorsa, aşağıdaki adresi tarayıcınıza kopyalayabilirsiniz:<br>
                        <span style="word-break: break-all; color: #3498db;">%s</span>
                    </p>
                    
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    
                    <p style="font-size: 14px; color: #888; text-align: center;">
                        EduConnect Ekibi<br>
                        <small>Bu e-posta otomatik olarak gönderilmiştir. Lütfen yanıtlamayınız.</small>
                    </p>
                </div>
            </body>
            </html>
            """,
            greeting,
            message.getResetLink(),
            message.getResetLink()
        );
    }
}

