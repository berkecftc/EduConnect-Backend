package com.educonnect.authservices.dto.response;

import com.educonnect.authservices.models.AdminAuditEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAuditPage(List<Entry> entries, int page, int size, long totalElements) {

    public record Entry(UUID id, UUID actorId, String actorEmail, String action, String targetType,
                        String targetId, String details, Instant createdAt) {

        public static Entry from(AdminAuditEntry entry) {
            return new Entry(entry.getId(), entry.getActorId(), entry.getActorEmail(), entry.getAction(),
                    entry.getTargetType(), entry.getTargetId(), entry.getDetails(), entry.getCreatedAt());
        }
    }
}
