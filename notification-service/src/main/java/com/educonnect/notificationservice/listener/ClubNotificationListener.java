package com.educonnect.notificationservice.listener;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.ClubMembershipNotificationMessage;
import com.educonnect.notificationservice.dto.message.ClubNotificationMessage;
import com.educonnect.notificationservice.dto.message.ClubRoleChangeNotificationMessage;
import com.educonnect.notificationservice.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ClubNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ClubNotificationListener.class);

    private final NotificationDispatcher dispatcher;

    public ClubNotificationListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.CLUB_MEMBERSHIP_NOTIFICATION_QUEUE)
    public void handleMembershipNotification(ClubMembershipNotificationMessage message) {
        log.info("Club membership notification received: clubId={}, status={}", message.clubId(), message.status());
        String title = "APPROVED".equals(message.status())
                ? message.clubName() + " üyeliğiniz onaylandı"
                : message.clubName() + " üyelik başvurunuz hakkında";
        send(message.studentId(), "CLUB_MEMBERSHIP", title, message.message(), message.clubId());
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.CLUB_ROLE_CHANGE_NOTIFICATION_QUEUE)
    public void handleRoleChangeNotification(ClubRoleChangeNotificationMessage message) {
        log.info("Club role change notification received: clubId={}, type={}, status={}",
                message.clubId(), message.notificationType(), message.status());
        send(message.targetUserId(), "CLUB_ROLE_CHANGE", message.clubName() + " görev değişikliği", message.message(),
                message.clubId());
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.CLUB_GENERAL_NOTIFICATION_QUEUE)
    public void handleClubNotification(ClubNotificationMessage message) {
        log.info("Club notification received: clubId={}", message.clubId());
        send(message.targetUserId(), "CLUB_NOTICE", message.clubName() + ": " + message.subject(), message.message(),
                message.clubId());
    }

    private void send(UUID recipient, String type, String title, String body, UUID clubId) {
        if (recipient == null) {
            return;
        }
        dispatcher.dispatch(NotificationRequest.of(List.of(recipient), NotificationCategory.CLUB_MANAGEMENT, type, title,
                body == null ? "" : body, clubId != null ? "/clubs/" + clubId : null, null));
    }
}
