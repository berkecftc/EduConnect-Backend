package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.RoleChangeRequest;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class RoleChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RoleChangeNotifier.class);

    private static final String ROUTING_KEY_ROLE_CHANGE_NOTIFICATION = "club.role.change.notification";

    private final OutboxPublisher outboxPublisher;
    private final RoleChangeUserNames userNames;

    RoleChangeNotifier(OutboxPublisher outboxPublisher, RoleChangeUserNames userNames) {
        this.outboxPublisher = outboxPublisher;
        this.userNames = userNames;
    }

    static ClubPosition previousRoleOf(RoleChangeRequest request) {
        return request.getCurrentRole() != null ? request.getCurrentRole() : ClubPosition.MEMBER;
    }

    void notifyAdvisor(Club club, RoleChangeRequest request, String message) {
        String studentName = userNames.nameOf(request.getStudentId());
        send(club.getAcademicAdvisorId(), club, request.getStudentId(), studentName,
                previousRoleOf(request), request.getRequestedRole(),
                RoleChangeNotificationMessage.Status.PENDING, message,
                RoleChangeNotificationMessage.Type.ROLE_CHANGE_REQUEST);
    }

    void send(UUID targetUserId, Club club, UUID affectedStudentId, String affectedStudentName,
              ClubPosition previousRole, ClubPosition newRole, RoleChangeNotificationMessage.Status status,
              String message, RoleChangeNotificationMessage.Type type) {
        if (targetUserId == null) {
            return;
        }
        try {
            RoleChangeNotificationMessage notificationMessage = new RoleChangeNotificationMessage(
                    targetUserId,
                    club.getId(),
                    club.getName(),
                    affectedStudentId,
                    affectedStudentName,
                    previousRole.name(),
                    newRole.name(),
                    status.name(),
                    message,
                    type.name()
            );

            outboxPublisher.publish(
                    ClubRabbitMQConfig.CLUB_EXCHANGE_NAME,
                    ROUTING_KEY_ROLE_CHANGE_NOTIFICATION,
                    notificationMessage
            );
        } catch (Exception e) {
            log.error("Failed to send role change notification: {}", e.getMessage(), e);
        }
    }
}
