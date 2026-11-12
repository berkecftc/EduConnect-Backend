package com.educonnect.notificationservice.service;

import com.educonnect.common.web.LogValues;
import com.educonnect.common.security.LogMasking;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@educonnect.com}")
    private String fromAddress;


    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendSimpleEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Email sent successfully to: {} | Subject: {}", LogValues.safe(LogMasking.email(to)), LogValues.safe(subject));

        } catch (Exception e) {
            log.error("Error sending email to {}: {}", LogValues.safe(LogMasking.email(to)), LogValues.safe(e.getMessage()));
        }
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("HTML Email sent successfully to: {} | Subject: {}", LogValues.safe(LogMasking.email(to)), LogValues.safe(subject));

        } catch (MessagingException e) {
            log.error("Error sending HTML email to {}: {}", LogValues.safe(LogMasking.email(to)), LogValues.safe(e.getMessage()));
        }
    }

    public boolean sendNotification(String to, EmailContent content, String oneClickUnsubscribeUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(content.subject());
            helper.setText(content.html(), true);
            if (content.inlineImage() != null) {
                helper.addInline(content.inlineImage().contentId(), new ByteArrayResource(content.inlineImage().data()),
                        content.inlineImage().contentType());
            }
            if (oneClickUnsubscribeUrl != null) {
                message.setHeader("List-Unsubscribe", "<" + oneClickUnsubscribeUrl + ">");
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
            mailSender.send(message);
            return true;
        } catch (MessagingException | RuntimeException e) {
            log.error("Error sending notification email to {}: {}", LogValues.safe(LogMasking.email(to)), LogValues.safe(e.getMessage()));
            return false;
        }
    }

    public void sendHtmlEmailWithInlineImage(String to, String subject, String htmlBody,
                                             String contentId, byte[] image, String imageContentType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.addInline(contentId, new ByteArrayResource(image), imageContentType);

            mailSender.send(message);
            log.info("HTML Email sent successfully to: {} | Subject: {}", LogValues.safe(LogMasking.email(to)), LogValues.safe(subject));

        } catch (MessagingException e) {
            log.error("Error sending HTML email to {}: {}", LogValues.safe(LogMasking.email(to)), LogValues.safe(e.getMessage()));
        }
    }
}
