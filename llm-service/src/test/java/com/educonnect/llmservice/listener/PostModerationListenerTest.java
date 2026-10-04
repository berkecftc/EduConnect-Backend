package com.educonnect.llmservice.listener;

import com.educonnect.llmservice.client.PostServiceClient;
import com.educonnect.llmservice.config.LlmSafetyProperties;
import com.educonnect.llmservice.config.RabbitMQConfig;
import com.educonnect.llmservice.dto.event.PostModerationEvent;
import com.educonnect.llmservice.dto.moderation.ModerationDecision;
import com.educonnect.llmservice.dto.moderation.ModerationDecisionRequest;
import com.educonnect.llmservice.service.AiModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostModerationListenerTest {

    private final UUID postId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();

    @Mock
    private AiModerationService moderationService;

    @Mock
    private PostServiceClient postServiceClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PostModerationListener listener;

    @BeforeEach
    void setUp() {
        listener = new PostModerationListener(moderationService, postServiceClient, rabbitTemplate,
                new LlmSafetyProperties(null, null, new LlmSafetyProperties.Moderation(null, 2), null));
    }

    @Test
    void wordListHitsAreReportedAsSuchWithoutAskingTheModel() {
        when(moderationService.blockedTermHit("Başlık", "salak")).thenReturn(true);

        listener.handleModerationEvent(postEvent("salak"));

        ModerationDecisionRequest request = sentForPost();
        assertThat(request.decision()).isEqualTo("ZORBA");
        assertThat(request.source()).isEqualTo(ModerationDecisionRequest.WORDLIST);
        verify(moderationService, never()).classify(anyString(), anyString());
    }

    @Test
    void anUndecidedModelSendsTheContentToTheModeratorQueue() {
        when(moderationService.classify("Başlık", "belirsiz")).thenReturn(Optional.empty());

        listener.handleModerationEvent(postEvent("belirsiz"));

        ModerationDecisionRequest request = sentForPost();
        assertThat(request.decision()).isEqualTo(ModerationDecisionRequest.UNDECIDED);
        assertThat(request.source()).isEqualTo(ModerationDecisionRequest.LLM);
    }

    @Test
    void commentsAreAnsweredOnTheCommentEndpoint() {
        when(moderationService.classify(null, "güzel yorum")).thenReturn(Optional.of(ModerationDecision.TEMIZ));

        listener.handleModerationEvent(new PostModerationEvent(postId, null, "güzel yorum", UUID.randomUUID(), "COMMENT", commentId));

        ArgumentCaptor<ModerationDecisionRequest> request = ArgumentCaptor.forClass(ModerationDecisionRequest.class);
        verify(postServiceClient).applyCommentModerationDecision(eq(commentId.toString()), request.capture());
        assertThat(request.getValue().decision()).isEqualTo("TEMIZ");
        verify(postServiceClient, never()).applyModerationDecision(anyString(), any());
    }

    @Test
    void anUnreachablePostServiceLeavesTheEventOnTheReviewQueue() {
        PostModerationEvent event = postEvent("temiz");
        when(moderationService.classify("Başlık", "temiz")).thenReturn(Optional.of(ModerationDecision.TEMIZ));
        doThrow(new RuntimeException("down")).when(postServiceClient).applyModerationDecision(anyString(), any());

        listener.handleModerationEvent(event);

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.POST_MODERATION_REVIEW_QUEUE, event);
    }

    private PostModerationEvent postEvent(String content) {
        return new PostModerationEvent(postId, "Başlık", content, UUID.randomUUID(), null, null);
    }

    private ModerationDecisionRequest sentForPost() {
        ArgumentCaptor<ModerationDecisionRequest> request = ArgumentCaptor.forClass(ModerationDecisionRequest.class);
        verify(postServiceClient).applyModerationDecision(eq(postId.toString()), request.capture());
        return request.getValue();
    }
}
