package com.educonnect.notificationservice.listener;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.messaging.notification.TurkishDates;
import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.EventRegistrationMessage;
import com.educonnect.notificationservice.model.Notification;
import com.educonnect.notificationservice.service.EmailContent;
import com.educonnect.notificationservice.service.NotificationDispatcher;
import com.educonnect.notificationservice.service.QrCodeRenderer;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RegistrationNotificationListener {

    static final String QR_CONTENT_ID = "ticket-qr";

    private final NotificationDispatcher dispatcher;
    private final QrCodeRenderer qrCodeRenderer;

    public RegistrationNotificationListener(NotificationDispatcher dispatcher, QrCodeRenderer qrCodeRenderer) {
        this.dispatcher = dispatcher;
        this.qrCodeRenderer = qrCodeRenderer;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.NOTIFICATION_REGISTRATION_QUEUE)
    public void handleRegistration(EventRegistrationMessage message) {
        if (message.getStudentId() == null) {
            return;
        }
        String when = TurkishDates.format(message.getEventTime());
        String body = "\"" + message.getEventTitle() + "\" etkinliğine kaydınız alındı.\nZaman: " + when
                + "\nYer: " + (message.getLocation() == null ? "-" : message.getLocation())
                + "\nGirişte bilet QR kodunuzu görevliye gösterin.";
        NotificationRequest request = NotificationRequest.of(List.of(message.getStudentId()), NotificationCategory.EVENT,
                "EVENT_TICKET", "Biletiniz: " + message.getEventTitle(), body, "/me/tickets",
                message.getQrCode() != null ? "ticket:" + message.getQrCode() : null);
        dispatcher.dispatch(request, (notification, footer) -> ticketEmail(notification, message, when, footer));
    }

    private EmailContent ticketEmail(Notification notification, EventRegistrationMessage message, String when, String footer) {
        String html = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <div style="background-color: #f4f4f4; padding: 20px; text-align: center;">
                        <h2 style="color: #2c3e50;">Kaydınız alındı</h2>
                        <p><strong>%s</strong> etkinliğine kaydınız alındı.</p>
                        <div style="background-color: white; padding: 20px; border-radius: 8px; display: inline-block; margin-top: 10px;">
                            <p style="margin: 5px 0;"><strong>Zaman:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Yer:</strong> %s</p>
                            <hr style="border: 0; border-top: 1px solid #eee; margin: 15px 0;">
                            <p>Giriş için aşağıdaki QR kodu görevliye gösterin:</p>
                            <img src="cid:%s" alt="Bilet QR Kodu" style="border: 2px solid #333; padding: 5px; border-radius: 4px;"/>
                            <p style="font-size: 12px; color: #777; margin-top: 10px;">Bilet kodu: %s</p>
                        </div>
                    </div>
                    %s
                </body>
                </html>
                """.formatted(HtmlText.escape(message.getEventTitle()), HtmlText.escape(when),
                HtmlText.escape(message.getLocation()), QR_CONTENT_ID, HtmlText.escape(message.getQrCode()), footer);
        return new EmailContent(notification.getTitle(), html,
                new EmailContent.InlineImage(QR_CONTENT_ID, qrCodeRenderer.renderPng(message.getQrCode()), "image/png"));
    }
}
