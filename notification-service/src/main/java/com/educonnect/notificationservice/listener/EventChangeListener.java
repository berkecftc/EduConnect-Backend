package com.educonnect.notificationservice.listener;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.messaging.notification.TurkishDates;
import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.EventChangedMessage;
import com.educonnect.notificationservice.service.NotificationDispatcher;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EventChangeListener {

    private final NotificationDispatcher dispatcher;

    public EventChangeListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.NOTIFICATION_EVENT_CHANGED_QUEUE)
    public void handleEventChanged(EventChangedMessage message) {
        if (message == null || message.kind() == null || message.recipientIds() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid event change message");
        }
        if (message.recipientIds().isEmpty()) {
            return;
        }
        dispatcher.dispatch(NotificationRequest.of(message.recipientIds(), NotificationCategory.EVENT,
                "EVENT_" + message.kind(), subject(message), body(message),
                message.eventId() != null ? "/events/" + message.eventId() : null, null));
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
        StringBuilder text = new StringBuilder("Kayıtlı olduğunuz \"").append(message.title()).append("\" etkinliğinde değişiklik var.\n");
        switch (message.kind()) {
            case "POSTPONED" -> text.append("Yeni tarih: ").append(TurkishDates.format(message.startsAt())).append(" – ")
                    .append(TurkishDates.format(message.endsAt()))
                    .append("\nKaydınız geçerlidir; katılamayacaksanız kaydınızı iptal edebilirsiniz.");
            case "RELOCATED" -> text.append("Yeni yer: ").append(message.location());
            case "CANCELLED" -> text.append("Etkinlik iptal edildi.");
            default -> {
            }
        }
        if (message.reason() != null && !message.reason().isBlank()) {
            text.append("\nGerekçe: ").append(message.reason());
        }
        return text.toString();
    }
}
