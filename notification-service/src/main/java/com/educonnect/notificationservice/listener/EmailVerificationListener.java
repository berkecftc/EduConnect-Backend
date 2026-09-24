package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.EmailVerificationMessage;
import com.educonnect.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationListener {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationListener.class);

    private final EmailService emailService;

    public EmailVerificationListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.EMAIL_VERIFICATION_QUEUE)
    public void handleEmailVerification(EmailVerificationMessage message) {
        if (message == null || message.email() == null || message.verificationLink() == null) {
            log.warn("Geçersiz e-posta doğrulama mesajı alındı");
            return;
        }
        try {
            emailService.sendHtmlEmail(message.email(), "EduConnect - E-posta Adresinizi Doğrulayın", buildEmail(message));
            log.info("E-posta doğrulama bağlantısı gönderildi");
        } catch (Exception e) {
            log.error("E-posta doğrulama bağlantısı gönderilemedi: {}", e.getMessage(), e);
        }
    }

    static String buildEmail(EmailVerificationMessage message) {
        String greeting = message.firstName() != null && !message.firstName().isBlank()
                ? "Merhaba <strong>" + HtmlText.escape(message.firstName()) + "</strong>,"
                : "Merhaba,";
        String link = HtmlText.escape(message.verificationLink());
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; color: #333; background-color: #f9f9f9; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <h1 style="color: #3498db; margin: 0;">✉️ E-posta Doğrulama</h1>
                    </div>
                    <p style="font-size: 16px;">%s</p>
                    <p style="font-size: 16px;">
                        EduConnect başvurunuzu tamamlamak için e-posta adresinizi doğrulamanız gerekiyor.
                        Başvurunuz, e-posta adresiniz doğrulandıktan sonra yönetici onayına sunulur.
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #3498db; color: white; padding: 14px 35px; text-decoration: none; border-radius: 5px; font-size: 16px; font-weight: bold;">
                            E-postamı Doğrula
                        </a>
                    </div>
                    <div style="background-color: #fff3cd; border: 1px solid #ffc107; border-radius: 5px; padding: 15px; margin: 20px 0;">
                        Bu bağlantı <strong>%d saat</strong> geçerlidir. Bu başvuruyu siz yapmadıysanız bu e-postayı görmezden gelebilirsiniz.
                    </div>
                    <p style="font-size: 14px; color: #666;">
                        Bağlantı çalışmıyorsa aşağıdaki adresi tarayıcınıza kopyalayın:<br>
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
            """, greeting, link, message.validHours(), link);
    }
}
