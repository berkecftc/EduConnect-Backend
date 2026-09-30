package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.FinanceSettings;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubFinanceEntry;
import com.educonnect.clubservice.model.FinanceEntryType;
import com.educonnect.clubservice.repository.ClubFinanceEntryRepository;
import com.educonnect.clubservice.security.ClubAccess;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
class FinanceEntryApprovalHandler implements ApprovalHandler {

    private static final String SUBJECT = "Kulüp gelir-gider kaydı";

    private final ClubFinanceEntryRepository entryRepository;
    private final FinanceSettings financeSettings;
    private final ClubNotificationPublisher notificationPublisher;
    private final Clock clock = Clock.systemUTC();

    FinanceEntryApprovalHandler(ClubFinanceEntryRepository entryRepository,
                                FinanceSettings financeSettings,
                                ClubNotificationPublisher notificationPublisher) {
        this.entryRepository = entryRepository;
        this.financeSettings = financeSettings;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_FINANCE_ENTRY;
    }

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return !preparer.actingPresident();
    }

    @Override
    public boolean needsAdvisorApproval(ClubApprovalRequest request) {
        return financeSettings.needsAdvisor(entryOf(request).getAmount());
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        if (deciderId != null) {
            notificationPublisher.notifyUser(deciderId, club, SUBJECT,
                    "\"" + club.getName() + "\" kulübünün " + describe(entryOf(request)) + " onayınızı bekliyor.");
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubFinanceEntry entry = entryOf(request);
        entry.approve(clock.instant());
        entryRepository.save(entry);
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT,
                "\"" + club.getName() + "\" kulübünün " + describe(entry) + " onaylandı.");
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübünün " + describe(entryOf(request)) + " reddedildi.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message);
    }

    private static String describe(ClubFinanceEntry entry) {
        String kind = entry.getType() == FinanceEntryType.INCOME ? "gelir" : "gider";
        return entry.getAmount().toPlainString() + " TL tutarındaki " + kind + " kaydı (" + entry.getDescription() + ")";
    }

    private ClubFinanceEntry entryOf(ClubApprovalRequest request) {
        return entryRepository.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException("Finance entry missing for request " + request.getId()));
    }
}
