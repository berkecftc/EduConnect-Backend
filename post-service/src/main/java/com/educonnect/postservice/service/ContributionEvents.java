package com.educonnect.postservice.service;

import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.event.ActionType;
import com.educonnect.postservice.event.GamificationEvent;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ContributionEvents {

    private final OutboxPublisher outboxPublisher;

    public ContributionEvents(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void noteLiked(Post post, UUID liker) {
        appreciated(post, liker, ActionType.NOTE_LIKED);
    }

    public void noteSaved(Post post, UUID saver) {
        appreciated(post, saver, ActionType.NOTE_SAVED);
    }

    public void answerAccepted(UUID commentAuthor, UUID commentId) {
        if (commentAuthor == null) {
            return;
        }
        publish(RabbitMQConfig.ROUTING_KEY_GAMIFICATION_ANSWER_ACCEPTED,
                new GamificationEvent(commentAuthor, ActionType.ANSWER_ACCEPTED, commentId.toString(), OffsetDateTime.now(), commentId));
        restore(commentAuthor, commentId);
    }

    public void revoke(UUID author, UUID contentId) {
        revise(author, contentId, ActionType.POINTS_REVERSED, "revoke:");
    }

    public void restore(UUID author, UUID contentId) {
        revise(author, contentId, ActionType.POINTS_RESTORED, "restore:");
    }

    private void appreciated(Post post, UUID actor, ActionType action) {
        if (post.getCategory() != PostCategory.DERS_NOTU || post.getStatus() != PostStatus.PUBLISHED
                || post.getAuthorId() == null || post.getAuthorId().equals(actor)) {
            return;
        }
        publish(RabbitMQConfig.ROUTING_KEY_GAMIFICATION_NOTE_APPRECIATED,
                new GamificationEvent(post.getAuthorId(), action, post.getId() + ":" + actor, OffsetDateTime.now(), post.getId()));
    }

    private void revise(UUID author, UUID contentId, ActionType action, String prefix) {
        if (author == null) {
            return;
        }
        publish(RabbitMQConfig.ROUTING_KEY_GAMIFICATION_CONTENT_REVISED,
                new GamificationEvent(author, action, prefix + contentId + ":" + UUID.randomUUID(), OffsetDateTime.now(), contentId));
    }

    private void publish(String routingKey, GamificationEvent event) {
        outboxPublisher.publish(RabbitMQConfig.GAMIFICATION_EXCHANGE, routingKey, event);
    }
}
