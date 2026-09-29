package com.educonnect.llmservice.listener;

import com.educonnect.llmservice.client.PostServiceClient;
import com.educonnect.llmservice.config.LlmSafetyProperties;
import com.educonnect.llmservice.config.RabbitMQConfig;
import com.educonnect.llmservice.dto.event.PostModerationEvent;
import com.educonnect.llmservice.dto.moderation.ModerationDecision;
import com.educonnect.llmservice.dto.moderation.ModerationDecisionRequest;
import com.educonnect.llmservice.service.AiModerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PostModerationListener {

    private static final Logger log = LoggerFactory.getLogger(PostModerationListener.class);

    private final AiModerationService aiModerationService;
    private final PostServiceClient postServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final int maxAttempts;

    public PostModerationListener(AiModerationService aiModerationService,
                                  PostServiceClient postServiceClient,
                                  RabbitTemplate rabbitTemplate,
                                  LlmSafetyProperties properties) {
        this.aiModerationService = aiModerationService;
        this.postServiceClient = postServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.maxAttempts = properties.moderation().maxAttempts();
    }

    @RabbitListener(queues = RabbitMQConfig.POST_MODERATION_LLM_QUEUE)
    public void handleModerationEvent(PostModerationEvent event) {
        log.info("Moderation event received. postId={}, eventId={}", event.postId(), event.eventId());

        Optional<ModerationDecision> decision = Optional.empty();
        for (int attempt = 1; attempt <= maxAttempts && decision.isEmpty(); attempt++) {
            decision = aiModerationService.classify(event.title(), event.content());
        }

        if (decision.isEmpty()) {
            log.warn("Moderation undecided after {} attempts; post stays pending and is queued for manual review. postId={}",
                    maxAttempts, event.postId());
            sendToReview(event);
            return;
        }

        log.info("Moderation decision resolved. postId={}, decision={}", event.postId(), decision.get());
        try {
            postServiceClient.applyModerationDecision(
                    event.postId().toString(),
                    new ModerationDecisionRequest(decision.get().name(),
                            event.eventId() == null ? null : event.eventId().toString())
            );
        } catch (Exception ex) {
            log.error("Failed to notify post-service moderation result; queued for manual review. postId={}",
                    event.postId(), ex);
            sendToReview(event);
        }
    }

    private void sendToReview(PostModerationEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.POST_MODERATION_REVIEW_QUEUE, event);
    }
}
