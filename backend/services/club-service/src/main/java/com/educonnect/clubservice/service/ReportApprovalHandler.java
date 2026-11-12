package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.response.AcademicYears;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubReport;
import com.educonnect.clubservice.repository.ClubReportRepository;
import com.educonnect.clubservice.security.ClubAccess;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

abstract class ReportApprovalHandler implements ApprovalHandler {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private final ClubReportRepository reportRepository;
    private final ClubLeadershipService leadershipService;
    protected final ClubNotificationPublisher notificationPublisher;
    private final Clock clock = Clock.systemUTC();

    ReportApprovalHandler(ClubReportRepository reportRepository,
                          ClubLeadershipService leadershipService,
                          ClubNotificationPublisher notificationPublisher) {
        this.reportRepository = reportRepository;
        this.leadershipService = leadershipService;
        this.notificationPublisher = notificationPublisher;
    }

    abstract String title();

    abstract boolean presidentStage();

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return presidentStage() && !preparer.actingPresident();
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        if (deciderId != null) {
            notificationPublisher.notifyUser(deciderId, club, title(), describe(club, reportOf(request)) + " onayınızı bekliyor.");
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubReport report = reportOf(request);
        report.approve(clock.instant());
        reportRepository.save(report);
        String message = describe(club, report) + " onaylandı.";
        notificationPublisher.notifyUser(request.getPreparedBy(), club, title(), message);
        leadershipService.currentLeaderOf(club.getId())
                .filter(leaderId -> !leaderId.equals(request.getPreparedBy()))
                .ifPresent(leaderId -> notificationPublisher.notifyUser(leaderId, club, title(), message));
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        ClubReport report = reopen(request);
        String message = describe(club, report) + " reddedildi ve taslağa döndü.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, title(), message);
    }

    @Override
    public void onWithdrawn(Club club, ClubApprovalRequest request) {
        reopen(request);
    }

    private ClubReport reopen(ClubApprovalRequest request) {
        ClubReport report = reportOf(request);
        report.reopen();
        return reportRepository.save(report);
    }

    private String describe(Club club, ClubReport report) {
        return "\"" + club.getName() + "\" kulübünün " + AcademicYears.label(report.getAcademicYear()) + " " + title().toLowerCase(TURKISH);
    }

    private ClubReport reportOf(ClubApprovalRequest request) {
        return reportRepository.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException("Report missing for request " + request.getId()));
    }

    @Component
    static class Activity extends ReportApprovalHandler {

        Activity(ClubReportRepository reportRepository, ClubLeadershipService leadershipService,
                 ClubNotificationPublisher notificationPublisher) {
            super(reportRepository, leadershipService, notificationPublisher);
        }

        @Override
        public ApprovalType type() {
            return ApprovalType.CLUB_ACTIVITY_REPORT;
        }

        @Override
        String title() {
            return "Faaliyet raporu";
        }

        @Override
        boolean presidentStage() {
            return true;
        }
    }

    @Component
    static class Audit extends ReportApprovalHandler {

        Audit(ClubReportRepository reportRepository, ClubLeadershipService leadershipService,
              ClubNotificationPublisher notificationPublisher) {
            super(reportRepository, leadershipService, notificationPublisher);
        }

        @Override
        public ApprovalType type() {
            return ApprovalType.CLUB_AUDIT_REPORT;
        }

        @Override
        String title() {
            return "Denetim raporu";
        }

        @Override
        boolean presidentStage() {
            return false;
        }
    }
}
