package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubDecisionLogEntry;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.repository.ClubDecisionLogRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Component
public class ClubDecisionLog {

    private final ClubDecisionLogRepository repository;
    private final Clock clock;

    public ClubDecisionLog(ClubDecisionLogRepository repository) {
        this.repository = repository;
        this.clock = Clock.systemUTC();
    }

    public void record(ClubApprovalRequest request, DecisionAction action, UUID actorId, String detail) {
        repository.save(new ClubDecisionLogEntry(request.getClubId(), request.getId(), request.getType(), action,
                actorId, request.getSubjectUserId(), detail, clock.instant()));
    }

    public void record(UUID clubId, DecisionAction action, UUID actorId, UUID subjectUserId, String detail) {
        repository.save(new ClubDecisionLogEntry(clubId, null, null, action, actorId, subjectUserId, detail,
                clock.instant()));
    }

    public List<ClubDecisionLogEntry> entriesOf(UUID clubId) {
        return repository.findByClubIdOrderByCreatedAtDesc(clubId);
    }
}
