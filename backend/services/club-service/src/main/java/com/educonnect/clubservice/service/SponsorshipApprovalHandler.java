package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubFinanceEntry;
import com.educonnect.clubservice.model.ClubSponsorship;
import com.educonnect.clubservice.model.FinanceEntryType;
import com.educonnect.clubservice.repository.ClubFinanceEntryRepository;
import com.educonnect.clubservice.repository.ClubSponsorshipRepository;
import com.educonnect.clubservice.security.ClubAccess;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
class SponsorshipApprovalHandler implements ApprovalHandler {

    private static final String SUBJECT = "Sponsorluk";

    private final ClubSponsorshipRepository sponsorshipRepository;
    private final ClubFinanceEntryRepository entryRepository;
    private final MembershipTerms membershipTerms;
    private final ClubNotificationPublisher notificationPublisher;
    private final Clock clock = Clock.systemUTC();

    SponsorshipApprovalHandler(ClubSponsorshipRepository sponsorshipRepository,
                               ClubFinanceEntryRepository entryRepository,
                               MembershipTerms membershipTerms,
                               ClubNotificationPublisher notificationPublisher) {
        this.sponsorshipRepository = sponsorshipRepository;
        this.entryRepository = entryRepository;
        this.membershipTerms = membershipTerms;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_SPONSORSHIP;
    }

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return !preparer.actingPresident();
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        if (deciderId != null) {
            notificationPublisher.notifyUser(deciderId, club, SUBJECT, "\"" + club.getName() + "\" kulübünün "
                    + sponsorshipOf(request).getSponsorName() + " ile sponsorluk anlaşması onayınızı bekliyor.");
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubSponsorship sponsorship = sponsorshipOf(request);
        Instant now = clock.instant();
        sponsorship.approve(now);
        sponsorshipRepository.save(sponsorship);
        BigDecimal cash = sponsorship.getCashAmount();
        if (cash != null && cash.signum() > 0) {
            ClubFinanceEntry income = new ClubFinanceEntry(club.getId(), request.getId(), FinanceEntryType.INCOME, cash,
                    "Sponsorluk: " + sponsorship.getSponsorName(), sponsorship.getStartsOn(),
                    membershipTerms.academicYearOf(sponsorship.getStartsOn()), sponsorship.getPreparedBy(), now);
            income.linkSponsorship(sponsorship.getId());
            income.approve(now);
            entryRepository.save(income);
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, "\"" + club.getName() + "\" kulübünün "
                + sponsorship.getSponsorName() + " ile sponsorluk anlaşması onaylandı.");
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübünün " + sponsorshipOf(request).getSponsorName()
                + " ile sponsorluk anlaşması reddedildi.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message);
    }

    private ClubSponsorship sponsorshipOf(ClubApprovalRequest request) {
        return sponsorshipRepository.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException("Sponsorship missing for request " + request.getId()));
    }
}
