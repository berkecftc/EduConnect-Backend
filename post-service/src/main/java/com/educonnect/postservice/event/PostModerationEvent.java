package com.educonnect.postservice.event;

import java.io.Serializable;
import java.util.UUID;

/**
 * Post oluşturulduğunda veya güncellendiğinde RabbitMQ'ya fırlatılan olay.
 * eventId alanı idempotency kontrolü için kullanılır.
 */
public class PostModerationEvent implements Serializable {

    private UUID postId;
    private String title;
    private String content;
    private UUID eventId; // Her olay için benzersiz — Consumer tarafında idempotency sağlar

    public PostModerationEvent() {}

    public PostModerationEvent(UUID postId, String title, String content, UUID eventId) {
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.eventId = eventId;
    }

    // Getter & Setter
    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
}

