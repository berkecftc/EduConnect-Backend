package com.educonnect.notificationservice.listener;

import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.EmailVerificationMessage;
import com.educonnect.notificationservice.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
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
        if (message == null || message.email() == null) {
            throw new AmqpRejectAndDontRequeueException("Geçersiz e-posta doğrulama mesajı");
        }
        if (EmailVerificationMessage.EMAIL_CHANGED.equals(message.purpose())) {
            emailService.sendHtmlEmail(message.email(), "EduConnect - Giriş E-postanız Değişti", buildChangedNotice());
            log.info("E-posta değişikliği bildirimi eski adrese gönderildi");
            return;
        }
        if (message.verificationLink() == null) {
            throw new AmqpRejectAndDontRequeueException("Geçersiz e-posta doğrulama mesajı");
        }
        boolean change = EmailVerificationMessage.EMAIL_CHANGE.equals(message.purpose());
        emailService.sendHtmlEmail(message.email(),
                change ? "EduConnect - Yeni E-posta Adresinizi Doğrulayın" : "EduConnect - E-posta Adresinizi Doğrulayın",
                buildEmail(message));
        log.info("E-posta doğrulama bağlantısı gönderildi");
    }

    static String buildEmail(EmailVerificationMessage message) {
        String greeting = message.firstName() != null && !message.firstName().isBlank()
                ? "Merhaba <strong>" + HtmlText.escape(message.firstName()) + "</strong>,"
                : "Merhaba,";
        String link = HtmlText.escape(message.verificationLink());
        boolean change = EmailVerificationMessage.EMAIL_CHANGE.equals(message.purpose());
        String intro = change
                ? "Hesabınızın giriş e-postasını bu adresle değiştirmek istediniz. Değişiklik aşağıdaki bağlantıya tıkladığınızda tamamlanır; ardından yeni adresinizle tekrar giriş yapmanız gerekir."
                : "EduConnect başvurunuzu tamamlamak için e-posta adresinizi doğrulamanız gerekiyor. Başvurunuz, e-posta adresiniz doğrulandıktan sonra yönetici onayına sunulur.";
        String button = change ? "Yeni Adresimi Doğrula" : "E-postamı Doğrula";
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; color: #333; background-color: #f9f9f9; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <h1 style="color: #3498db; margin: 0;">✉️ E-posta Doğrulama</h1>
                    </div>
                    <p style="font-size: 16px;">%s</p>
                    <p style="font-size: 16px;">%s</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #3498db; color: white; padding: 14px 35px; text-decoration: none; border-radius: 5px; font-size: 16px; font-weight: bold;">
                            %s
                        </a>
                    </div>
                    <div style="background-color: #fff3cd; border: 1px solid #ffc107; border-radius: 5px; padding: 15px; margin: 20px 0;">
                        Bu bağlantı <strong>%d saat</strong> geçerlidir. Bu isteği siz yapmadıysanız bu e-postayı görmezden gelebilirsiniz.
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
            """, greeting, intro, link, button, message.validHours(), link);
    }

    static String buildChangedNotice() {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; color: #333; background-color: #f9f9f9; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                    <h1 style="color: #e67e22; margin: 0 0 20px 0; text-align: center;">Giriş E-postanız Değişti</h1>
                    <p style="font-size: 16px;">Merhaba,</p>
                    <p style="font-size: 16px;">
                        EduConnect hesabınızın giriş e-postası yeni bir adresle değiştirildi ve açık oturumlarınız kapatıldı.
                        Bundan sonra bu adresle giriş yapamazsınız.
                    </p>
                    <div style="background-color: #fdecea; border: 1px solid #e74c3c; border-radius: 5px; padding: 15px; margin: 20px 0;">
                        Bu değişikliği siz yapmadıysanız hemen yöneticiyle iletişime geçin.
                    </div>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="font-size: 14px; color: #888; text-align: center;">
                        EduConnect Ekibi<br>
                        <small>Bu e-posta otomatik olarak gönderilmiştir. Lütfen yanıtlamayınız.</small>
                    </p>
                </div>
            </body>
            </html>
            """;
    }
}
