package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.EventClient;
import com.educonnect.clubservice.dto.response.ClubEventStatistics;
import com.educonnect.clubservice.dto.response.ReportResponse;
import com.educonnect.clubservice.dto.response.ReportSnapshotResponse;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubBudget;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubReport;
import com.educonnect.clubservice.model.FinanceEntryType;
import com.educonnect.clubservice.model.ReportSnapshot;
import com.educonnect.clubservice.model.ReportType;
import com.educonnect.clubservice.repository.ClubAnnouncementRepository;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubBudgetRepository;
import com.educonnect.clubservice.repository.ClubFinanceEntryRepository;
import com.educonnect.clubservice.repository.ClubMeetingDecisionRepository;
import com.educonnect.clubservice.repository.ClubMeetingRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubReportRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAccess;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ClubReportService {

    private final ClubRepository clubRepository;
    private final ClubReportRepository reportRepository;
    private final ClubApprovalRequestRepository requestRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubAnnouncementRepository announcementRepository;
    private final ClubMeetingRepository meetingRepository;
    private final ClubMeetingDecisionRepository decisionRepository;
    private final ClubBudgetRepository budgetRepository;
    private final ClubFinanceEntryRepository entryRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubApprovalEngine approvalEngine;
    private final MembershipTerms membershipTerms;
    private final EventClient eventClient;

    public ClubReportService(ClubRepository clubRepository,
                             ClubReportRepository reportRepository,
                             ClubApprovalRequestRepository requestRepository,
                             ClubMembershipRepository membershipRepository,
                             ClubAnnouncementRepository announcementRepository,
                             ClubMeetingRepository meetingRepository,
                             ClubMeetingDecisionRepository decisionRepository,
                             ClubBudgetRepository budgetRepository,
                             ClubFinanceEntryRepository entryRepository,
                             ClubAuthorizationService clubAuthorizationService,
                             ClubApprovalEngine approvalEngine,
                             MembershipTerms membershipTerms,
                             EventClient eventClient) {
        this.clubRepository = clubRepository;
        this.reportRepository = reportRepository;
        this.requestRepository = requestRepository;
        this.membershipRepository = membershipRepository;
        this.announcementRepository = announcementRepository;
        this.meetingRepository = meetingRepository;
        this.decisionRepository = decisionRepository;
        this.budgetRepository = budgetRepository;
        this.entryRepository = entryRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.approvalEngine = approvalEngine;
        this.membershipTerms = membershipTerms;
        this.eventClient = eventClient;
    }

    public static ReportType typeOf(String path) {
        try {
            return ReportType.valueOf(path.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("REPORT_TYPE_NOT_FOUND", "Rapor türü bulunamadı.");
        }
    }

    public ReportResponse writeActivity(UUID clubId, UUID userId, int academicYear, String summary) {
        ClubReport report = editableDraft(clubId, userId, ReportType.ACTIVITY, academicYear, ClubPermission.PREPARE_ACTIVITY_REPORT);
        report.writeBody(blankToNull(summary), null, userId, Instant.now());
        return ReportResponse.of(reportRepository.save(report));
    }

    public ReportResponse writeFinanceNote(UUID clubId, UUID userId, int academicYear, String financeNote) {
        ClubReport report = editableDraft(clubId, userId, ReportType.ACTIVITY, academicYear, ClubPermission.PREPARE_FINANCE);
        report.writeFinanceNote(blankToNull(financeNote), userId, Instant.now());
        return ReportResponse.of(reportRepository.save(report));
    }

    public ReportResponse writeAudit(UUID clubId, UUID userId, int academicYear, String findings, String recommendations) {
        ClubReport report = editableDraft(clubId, userId, ReportType.AUDIT, academicYear, ClubPermission.PREPARE_AUDIT_REPORT);
        report.writeBody(blankToNull(findings), blankToNull(recommendations), userId, Instant.now());
        return ReportResponse.of(reportRepository.save(report));
    }

    public ClubApprovalRequest submit(UUID clubId, UUID userId, ReportType type, int academicYear, String note) {
        Club club = openClub(clubId);
        clubAuthorizationService.require(clubId, userId, preparePermission(type));
        ClubReport report = reportRepository.findByClubIdAndTypeAndAcademicYear(clubId, type, academicYear)
                .orElseThrow(() -> new NotFoundException("REPORT_NOT_FOUND", "Rapor taslağı bulunamadı."));
        if (!report.isDraft()) {
            throw new ConflictException("REPORT_LOCKED", "Rapor zaten gönderilmiş.");
        }
        if (report.getBody() == null) {
            throw new BadRequestException("REPORT_INCOMPLETE", "Rapor metni boş olamaz.");
        }
        ReportSnapshot snapshot = snapshotOf(clubId, academicYear);
        Instant now = Instant.now();
        ApprovalType approvalType = type == ReportType.ACTIVITY ? ApprovalType.CLUB_ACTIVITY_REPORT : ApprovalType.CLUB_AUDIT_REPORT;
        ClubApprovalRequest saved = requestRepository.save(new ClubApprovalRequest(clubId, approvalType, userId, null, null,
                null, note, now));
        report.submit(saved.getId(), snapshot, userId, now);
        reportRepository.save(report);
        return approvalEngine.submit(club, saved);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> reportsOf(UUID clubId, UUID userId, Integer year) {
        ClubAccess access = readableAccess(clubId, userId);
        int academicYear = year != null ? year : membershipTerms.currentAcademicYear();
        return reportRepository.findByClubIdAndAcademicYearOrderByType(clubId, academicYear).stream()
                .filter(report -> canView(report, access))
                .map(ReportResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse reportOf(UUID clubId, UUID userId, ReportType type, int academicYear) {
        ClubAccess access = readableAccess(clubId, userId);
        return reportRepository.findByClubIdAndTypeAndAcademicYear(clubId, type, academicYear)
                .filter(report -> canView(report, access))
                .map(ReportResponse::of)
                .orElseThrow(() -> new NotFoundException("REPORT_NOT_FOUND", "Rapor bulunamadı."));
    }

    @Transactional(readOnly = true)
    public ReportSnapshotResponse preview(UUID clubId, UUID userId, int academicYear) {
        readableAccess(clubId, userId);
        requireReportableYear(academicYear);
        return ReportSnapshotResponse.of(snapshotOf(clubId, academicYear));
    }

    private ClubReport editableDraft(UUID clubId, UUID userId, ReportType type, int academicYear, ClubPermission permission) {
        openClub(clubId);
        clubAuthorizationService.require(clubId, userId, permission);
        requireReportableYear(academicYear);
        ClubReport report = reportRepository.findByClubIdAndTypeAndAcademicYear(clubId, type, academicYear)
                .orElseGet(() -> new ClubReport(clubId, type, academicYear, userId, Instant.now()));
        if (!report.isDraft()) {
            throw new ConflictException("REPORT_LOCKED", "Gönderilmiş rapor düzenlenemez.");
        }
        return report;
    }

    private ReportSnapshot snapshotOf(UUID clubId, int academicYear) {
        LocalDateTime from = membershipTerms.academicYearStart(academicYear).atStartOfDay();
        LocalDateTime to = membershipTerms.academicYearEnd(academicYear).plusDays(1).atStartOfDay();
        ZoneId zone = ZoneId.systemDefault();
        ClubBudget budget = budgetRepository
                .findFirstByClubIdAndAcademicYearAndApprovedAtIsNotNullOrderByApprovedAtDesc(clubId, academicYear)
                .orElse(null);
        ClubEventStatistics events = eventClient.clubStatistics(clubId, from.toString(), to.toString());
        return new ReportSnapshot(
                membershipRepository.countByClubIdAndIsActive(clubId, true),
                membershipRepository.countByClubIdAndEndedAtGreaterThanEqualAndEndedAtLessThan(clubId, from, to),
                announcementRepository.countByClubIdAndPublishedAtGreaterThanEqualAndPublishedAtLessThan(clubId,
                        from.atZone(zone).toInstant(), to.atZone(zone).toInstant()),
                meetingRepository.countByClubIdAndAcademicYearAndApprovedAtIsNotNull(clubId, academicYear),
                decisionRepository.countByClubIdAndAcademicYearAndDecisionNumberIsNotNull(clubId, academicYear),
                budget != null ? budget.getPlannedIncome() : null,
                budget != null ? budget.getPlannedExpense() : null,
                entryRepository.approvedTotal(clubId, academicYear, FinanceEntryType.INCOME),
                entryRepository.approvedTotal(clubId, academicYear, FinanceEntryType.EXPENSE),
                events.totalEvents(), events.completedEvents(), events.cancelledEvents(), events.registrations(),
                events.attendances());
    }

    private void requireReportableYear(int academicYear) {
        int current = membershipTerms.currentAcademicYear();
        if (academicYear != current && academicYear != current - 1) {
            throw new BadRequestException("INVALID_ACADEMIC_YEAR", "Rapor yalnızca bu veya geçen akademik yıl için hazırlanabilir.");
        }
    }

    private ClubAccess readableAccess(UUID clubId, UUID userId) {
        findClub(clubId);
        ClubAccess access = clubAuthorizationService.accessOf(clubId, userId);
        if (!access.has(ClubPermission.VIEW_DECISIONS) && !access.has(ClubPermission.PREPARE_AUDIT_REPORT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için kulüpte yetkiniz yok.");
        }
        return access;
    }

    private static boolean canView(ClubReport report, ClubAccess access) {
        if (report.getType() == ReportType.AUDIT && report.isDraft()) {
            return access.position() == ClubPosition.AUDITOR;
        }
        return access.has(ClubPermission.VIEW_DECISIONS);
    }

    private static ClubPermission preparePermission(ReportType type) {
        return type == ReportType.ACTIVITY ? ClubPermission.PREPARE_ACTIVITY_REPORT : ClubPermission.PREPARE_AUDIT_REPORT;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Club openClub(UUID clubId) {
        Club club = findClub(clubId);
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulüpte rapor hazırlanamaz.");
        }
        return club;
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }
}
