package com.educonnect.postservice.service;

import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationActor;
import com.educonnect.postservice.model.ModerationRecord;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.repository.ModerationRecordRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ModerationLog {

    private final ModerationRecordRepository recordRepository;
    private final PostNotifier notifier;

    public ModerationLog(ModerationRecordRepository recordRepository, PostNotifier notifier) {
        this.recordRepository = recordRepository;
        this.notifier = notifier;
    }

    public void record(ModerationTarget target, UUID targetId, UUID postId, ModerationAction action,
                       ModerationActor actor, UUID actorId, String reason) {
        recordRepository.save(new ModerationRecord(target, targetId, postId, action, actor, actorId, reason, Instant.now()));
        notifier.moderationDecision(target, targetId, postId, action, actor, reason);
    }
}
