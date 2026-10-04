package com.educonnect.notificationservice.service;

import com.educonnect.common.web.NotFoundException;
import com.educonnect.notificationservice.config.NotificationProperties;
import com.educonnect.notificationservice.dto.response.NotificationResponse;
import com.educonnect.notificationservice.model.Notification;
import com.educonnect.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    private final NotificationRepository repository;
    private final NotificationProperties properties;

    public InboxService(NotificationRepository repository, NotificationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> inbox(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? repository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : repository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = repository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "Bildirim bulunamadı."));
        notification.markRead(Instant.now());
        return NotificationResponse.from(repository.save(notification));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, Instant.now());
    }

    @Scheduled(cron = "${educonnect.notification.retention-cron:0 30 3 * * *}", zone = "Europe/Istanbul")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteOlderThan(Instant.now().minus(properties.retention()));
        if (removed > 0) {
            log.info("Removed {} notification(s) past the retention period", removed);
        }
    }
}
