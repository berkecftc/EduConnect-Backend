package com.educonnect.eventservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.messaging.notification.TurkishDates;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;

@Component
public class EventNotifier {

    private static final Logger log = LoggerFactory.getLogger(EventNotifier.class);

    private final OutboxPublisher outboxPublisher;
    private final ClubClient clubClient;

    public EventNotifier(OutboxPublisher outboxPublisher, ClubClient clubClient) {
        this.outboxPublisher = outboxPublisher;
        this.clubClient = clubClient;
    }

    public void notify(Collection<UUID> recipientIds, NotificationCategory category, String type, Event event,
                       String title, String body) {
        if (recipientIds == null) {
            return;
        }
        List<UUID> recipients = recipientIds.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) {
            return;
        }
        outboxPublisher.publish(NotificationRequest.EXCHANGE, NotificationRequest.ROUTING_KEY,
                NotificationRequest.of(recipients, category, type, title, body, "/events/" + event.getId(), null));
    }

    public void awaitingApproval(Event event, UUID actorId) {
        if (event.isCampus()) {
            return;
        }
        String when = TurkishDates.format(event.getStartsAt());
        if (event.getStatus() == EventStatus.PENDING_PRESIDENT) {
            List<UUID> leaders = leadersOf(event.getClubId()).stream().filter(id -> !id.equals(actorId)).toList();
            notify(leaders, NotificationCategory.CLUB_MANAGEMENT, "EVENT_APPROVAL_REQUEST", event,
                    event.getClubName() + ": Onayınızı bekleyen etkinlik",
                    "\"" + event.getTitle() + "\" (" + when + ") etkinliği başkan onayınızı bekliyor.");
        } else if (event.getStatus() == EventStatus.PENDING) {
            UUID advisor = advisorOf(event.getClubId());
            if (advisor == null || advisor.equals(actorId)) {
                return;
            }
            notify(List.of(advisor), NotificationCategory.CLUB_MANAGEMENT, "EVENT_APPROVAL_REQUEST", event,
                    event.getClubName() + ": Danışman onayı bekleyen etkinlik",
                    "\"" + event.getTitle() + "\" (" + when + ") etkinliği danışman onayınızı bekliyor.");
        }
    }

    public void decided(Event event, boolean approved, String stage, String reason) {
        String title = event.getClubName() != null ? event.getClubName() + ": " : "";
        String body;
        if (approved) {
            title += "Etkinliğiniz yayımlandı";
            body = "\"" + event.getTitle() + "\" etkinliği danışman tarafından onaylandı ve yayımlandı.";
        } else {
            title += "Etkinliğiniz reddedildi";
            body = "\"" + event.getTitle() + "\" etkinliği " + stage + " tarafından reddedildi.";
            if (reason != null && !reason.isBlank()) {
                body += "\nGerekçe: " + reason.strip();
            }
            body += "\nEtkinliği düzenleyip yeniden onaya gönderebilirsiniz.";
        }
        notify(Collections.singletonList(event.getCreatedByStudentId()), NotificationCategory.CLUB_MANAGEMENT,
                approved ? "EVENT_APPROVED" : "EVENT_REJECTED", event, title, body);
    }

    private List<UUID> leadersOf(UUID clubId) {
        try {
            List<UUID> leaders = clubClient.getClubLeaderIds(clubId);
            return leaders != null ? leaders : List.of();
        } catch (RuntimeException e) {
            log.warn("Club leaders could not be resolved for notification: clubId={}, error={}", clubId, e.getMessage());
            return List.of();
        }
    }

    private UUID advisorOf(UUID clubId) {
        try {
            return clubClient.getClubAdvisorId(clubId);
        } catch (RuntimeException e) {
            log.warn("Club advisor could not be resolved for notification: clubId={}, error={}", clubId, e.getMessage());
            return null;
        }
    }
}
