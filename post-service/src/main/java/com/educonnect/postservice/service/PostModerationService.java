package com.educonnect.postservice.service;

import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.dto.ModerationDecision;
import com.educonnect.postservice.event.ActionType;
import com.educonnect.postservice.event.GamificationEvent;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PostModerationService {

    private static final Logger log = LoggerFactory.getLogger(PostModerationService.class);

    private final PostRepository postRepository;
    private final OutboxPublisher outboxPublisher;

    public PostModerationService(PostRepository postRepository, OutboxPublisher outboxPublisher) {
        this.postRepository = postRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public void applyModeration(UUID postId, ModerationDecision decision, UUID eventId) {
        Optional<Post> optionalPost = postRepository.findById(postId);
        if (optionalPost.isEmpty()) {
            log.warn("Post not found during moderation. postId={}, eventId={}", postId, eventId);
            return;
        }

        Post post = optionalPost.get();
        if (post.getStatus() != PostStatus.PENDING) {
            log.info("Post already moderated (status={}). postId={}, eventId={}",
                    post.getStatus(), postId, eventId);
            return;
        }

        if (decision == ModerationDecision.ZORBA) {
            post.setStatus(PostStatus.REJECTED);
            postRepository.save(post);
            log.warn("Post rejected by moderation. postId={}, eventId={}", postId, eventId);
            return;
        }

        post.setStatus(PostStatus.PUBLISHED);
        postRepository.save(post);
        log.info("Post published by moderation. postId={}, eventId={}", postId, eventId);

        if (post.getCategory() == PostCategory.DERS_NOTU && post.getAuthorId() != null) {
            publishGamificationEvent(post);
        }
    }

    private void publishGamificationEvent(Post post) {
        GamificationEvent gamificationEvent = new GamificationEvent(
                post.getAuthorId(),
                ActionType.POST_PUBLISHED,
                post.getId().toString(),
                OffsetDateTime.now()
        );

        outboxPublisher.publish(
                RabbitMQConfig.GAMIFICATION_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_GAMIFICATION_POST_PUBLISHED,
                gamificationEvent
        );
        log.info("Gamification event queued. postId={}", post.getId());
    }
}

