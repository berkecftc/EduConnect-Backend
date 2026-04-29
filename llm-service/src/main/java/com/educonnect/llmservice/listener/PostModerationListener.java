package com.educonnect.llmservice.listener;

import com.educonnect.llmservice.client.PostServiceClient;
import com.educonnect.llmservice.config.RabbitMQConfig;
import com.educonnect.llmservice.dto.event.PostModerationEvent;
import com.educonnect.llmservice.dto.moderation.ModerationDecision;
import com.educonnect.llmservice.dto.moderation.ModerationDecisionRequest;
import com.educonnect.llmservice.service.AiModerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PostModerationListener {

    private static final Logger log = LoggerFactory.getLogger(PostModerationListener.class);

    private final AiModerationService aiModerationService;
    private final PostServiceClient postServiceClient;

    public PostModerationListener(AiModerationService aiModerationService,
                                  PostServiceClient postServiceClient) {
        this.aiModerationService = aiModerationService;
        this.postServiceClient = postServiceClient;
    }

    @RabbitListener(queues = RabbitMQConfig.POST_MODERATION_LLM_QUEUE)
    public void handleModerationEvent(PostModerationEvent event) {
        log.info("Moderation event received. postId={}, eventId={}", event.postId(), event.eventId());

        try {
            Optional<ModerationDecision> decision =
                    aiModerationService.classify(event.title(), event.content());

            if (decision.isEmpty()) {
                log.warn("Moderation decision missing, leaving post pending. postId={}", event.postId());
                return;
            }

            log.info("Moderation decision resolved. postId={}, decision={}", event.postId(), decision.get());

            postServiceClient.applyModerationDecision(
                    event.postId().toString(),
                    new ModerationDecisionRequest(decision.get().name(),
                            event.eventId() == null ? null : event.eventId().toString())
            );
        } catch (Exception ex) {
            log.error("Failed to notify post-service moderation result. postId={}", event.postId(), ex);
        }
    }
}
