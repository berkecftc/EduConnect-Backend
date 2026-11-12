package com.educonnect.postservice.messaging;

import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.service.PostModerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ModerationReviewListener {

    private static final Logger log = LoggerFactory.getLogger(ModerationReviewListener.class);

    private final PostModerationService moderationService;

    public ModerationReviewListener(PostModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @RabbitListener(queues = RabbitMQConfig.POST_MODERATION_REVIEW_QUEUE)
    public void handle(PostModerationEvent event) {
        log.info("Undecided moderation event moved to the moderator queue. postId={}, commentId={}",
                event.getPostId(), event.getCommentId());
        moderationService.markUndecided(event);
    }
}
