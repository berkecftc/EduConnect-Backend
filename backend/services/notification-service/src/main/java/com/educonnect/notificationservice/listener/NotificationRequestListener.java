package com.educonnect.notificationservice.listener;

import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.service.NotificationDispatcher;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationRequestListener {

    private final NotificationDispatcher dispatcher;

    public NotificationRequestListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.NOTIFICATION_REQUEST_QUEUE)
    public void handle(NotificationRequest request) {
        dispatcher.dispatch(request);
    }
}
