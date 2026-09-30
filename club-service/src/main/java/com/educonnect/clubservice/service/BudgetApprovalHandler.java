package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.response.AcademicYears;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubBudget;
import com.educonnect.clubservice.repository.ClubBudgetRepository;
import com.educonnect.clubservice.security.ClubAccess;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
class BudgetApprovalHandler implements ApprovalHandler {

    private static final String SUBJECT = "Kulüp bütçesi";

    private final ClubBudgetRepository budgetRepository;
    private final ClubNotificationPublisher notificationPublisher;
    private final Clock clock = Clock.systemUTC();

    BudgetApprovalHandler(ClubBudgetRepository budgetRepository, ClubNotificationPublisher notificationPublisher) {
        this.budgetRepository = budgetRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_BUDGET;
    }

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return !preparer.actingPresident();
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        if (deciderId != null) {
            notificationPublisher.notifyUser(deciderId, club, SUBJECT, "\"" + club.getName() + "\" kulübünün "
                    + AcademicYears.label(budgetOf(request).getAcademicYear()) + " bütçesi onayınızı bekliyor.");
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubBudget budget = budgetOf(request);
        budget.approve(clock.instant());
        budgetRepository.save(budget);
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, "\"" + club.getName() + "\" kulübünün "
                + AcademicYears.label(budget.getAcademicYear()) + " bütçesi onaylandı.");
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübünün " + AcademicYears.label(budgetOf(request).getAcademicYear())
                + " bütçesi reddedildi.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message);
    }

    private ClubBudget budgetOf(ClubApprovalRequest request) {
        return budgetRepository.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException("Budget missing for request " + request.getId()));
    }
}
