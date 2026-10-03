package com.educonnect.notificationservice.model;

import com.educonnect.common.messaging.notification.NotificationCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(length = 500)
    private String link;

    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 12)
    private EmailStatus emailStatus = EmailStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(UUID recipientId, NotificationCategory category, String type, String title, String body,
                        String link, String dedupKey, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.recipientId = recipientId;
        this.category = category;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
        this.dedupKey = dedupKey;
        this.createdAt = createdAt;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public void emailStatus(EmailStatus status) {
        this.emailStatus = status;
    }

    public UUID getId() { return id; }
    public UUID getRecipientId() { return recipientId; }
    public NotificationCategory getCategory() { return category; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public String getDedupKey() { return dedupKey; }
    public EmailStatus getEmailStatus() { return emailStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
