package com.educonnect.notificationservice.repository;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.notificationservice.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreference.Key> {

    List<NotificationPreference> findByUserId(UUID userId);

    List<NotificationPreference> findByUserIdInAndCategory(Collection<UUID> userIds, NotificationCategory category);
}
