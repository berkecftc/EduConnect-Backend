package com.educonnect.notificationservice.listener;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.messaging.notification.TurkishDates;
import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.EventCreatedMessage;
import com.educonnect.notificationservice.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Component
public class EventNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EventNotificationListener.class);

    private final NotificationDispatcher dispatcher;
    private final RestTemplate restTemplate;

    public EventNotificationListener(NotificationDispatcher dispatcher, RestTemplate restTemplate) {
        this.dispatcher = dispatcher;
        this.restTemplate = restTemplate;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.NOTIFICATION_EVENT_QUEUE)
    public void handleEventCreated(EventCreatedMessage message) {
        UUID clubId = message.getClubId();
        if (clubId == null) {
            log.info("Event {} has no club; members are not notified", message.getEventId());
            return;
        }
        List<UUID> memberIds = restTemplate.exchange("http://CLUB-SERVICE/api/clubs/internal/" + clubId + "/members/ids",
                HttpMethod.GET, null, new ParameterizedTypeReference<List<UUID>>() {
                }).getBody();
        if (memberIds == null || memberIds.isEmpty()) {
            log.info("Club {} has no members to notify about event {}", clubId, message.getEventId());
            return;
        }
        String body = message.getClubName() + " kulübü \"" + message.getTitle() + "\" etkinliğini duyurdu.\nZaman: "
                + TurkishDates.format(message.getEventTime())
                + (message.getLocation() != null && !message.getLocation().isBlank() ? "\nYer: " + message.getLocation() : "");
        dispatcher.dispatch(NotificationRequest.of(memberIds, NotificationCategory.CLUB_NEWS, "EVENT_ANNOUNCED",
                "Yeni etkinlik: " + message.getTitle(), body, "/events/" + message.getEventId(),
                "event-announced:" + message.getEventId()));
    }
}
