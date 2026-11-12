package com.educonnect.postservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class ModerationTimeoutJob {

    private final PostModerationService moderationService;
    private final Duration pendingTimeout;

    public ModerationTimeoutJob(PostModerationService moderationService,
                                @Value("${educonnect.post.moderation.pending-timeout:PT30M}") Duration pendingTimeout) {
        this.moderationService = moderationService;
        this.pendingTimeout = pendingTimeout;
    }

    @Scheduled(cron = "${educonnect.post.moderation.timeout-cron:0 */5 * * * *}")
    public void escalateStalePending() {
        moderationService.escalateStale(Instant.now().minus(pendingTimeout));
    }
}
