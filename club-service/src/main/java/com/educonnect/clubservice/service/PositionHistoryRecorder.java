package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ClubPositionTerm;
import com.educonnect.clubservice.model.PositionChanged;
import com.educonnect.clubservice.model.PositionEndReason;
import com.educonnect.clubservice.repository.ClubPositionTermRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Transactional
public class PositionHistoryRecorder {

    private final ClubPositionTermRepository termRepository;

    public PositionHistoryRecorder(ClubPositionTermRepository termRepository) {
        this.termRepository = termRepository;
    }

    @EventListener
    public void on(PositionChanged change) {
        if (change.from() != null && change.from().isManagement()) {
            termRepository.findByClubIdAndStudentIdAndEndedAtIsNull(change.clubId(), change.studentId())
                    .forEach(term -> term.end(change.at(), change.reason()));
            termRepository.flush();
        }
        if (change.to() != null && change.to().isManagement()) {
            termRepository.save(new ClubPositionTerm(change.clubId(), change.studentId(), change.to(), change.at()));
        }
    }

    public void closeAll(UUID clubId, LocalDateTime at, PositionEndReason reason) {
        termRepository.findByClubIdAndEndedAtIsNull(clubId).forEach(term -> term.end(at, reason));
    }
}
