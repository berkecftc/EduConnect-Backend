package com.educonnect.postservice.event;

import com.educonnect.postservice.model.ModerationTarget;

import java.io.Serializable;
import java.util.UUID;

public class PostModerationEvent implements Serializable {

    private UUID postId;
    private String title;
    private String content;
    private UUID eventId;
    private String targetType;
    private UUID commentId;

    public PostModerationEvent() {}

    public PostModerationEvent(UUID postId, String title, String content, UUID eventId) {
        this(postId, title, content, eventId, ModerationTarget.POST, null);
    }

    public PostModerationEvent(UUID postId, String title, String content, UUID eventId,
                               ModerationTarget targetType, UUID commentId) {
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.eventId = eventId;
        this.targetType = targetType.name();
        this.commentId = commentId;
    }

    public static PostModerationEvent forComment(UUID postId, UUID commentId, String content) {
        return new PostModerationEvent(postId, null, content, UUID.randomUUID(), ModerationTarget.COMMENT, commentId);
    }

    public boolean comment() {
        return ModerationTarget.COMMENT.name().equals(targetType) && commentId != null;
    }

    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public UUID getCommentId() { return commentId; }
    public void setCommentId(UUID commentId) { this.commentId = commentId; }
}
