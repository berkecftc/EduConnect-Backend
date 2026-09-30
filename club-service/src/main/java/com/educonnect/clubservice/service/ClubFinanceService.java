package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.request.BudgetRequest;
import com.educonnect.clubservice.dto.request.FinanceEntryForm;
import com.educonnect.clubservice.dto.request.SponsorshipForm;
import com.educonnect.clubservice.dto.response.AcademicYears;
import com.educonnect.clubservice.dto.response.BudgetResponse;
import com.educonnect.clubservice.dto.response.FinanceEntryResponse;
import com.educonnect.clubservice.dto.response.FinanceSummaryResponse;
import com.educonnect.clubservice.dto.response.SponsorshipResponse;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubBudget;
import com.educonnect.clubservice.model.ClubFinanceEntry;
import com.educonnect.clubservice.model.ClubSponsorship;
import com.educonnect.clubservice.model.FinanceEntryType;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubBudgetRepository;
import com.educonnect.clubservice.repository.ClubFinanceEntryRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.repository.ClubSponsorshipRepository;
import com.educonnect.clubservice.security.ClubAccess;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClubFinanceService {

    private final ClubRepository clubRepository;
    private final ClubApprovalRequestRepository requestRepository;
    private final ClubBudgetRepository budgetRepository;
    private final ClubFinanceEntryRepository entryRepository;
    private final ClubSponsorshipRepository sponsorshipRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubApprovalEngine approvalEngine;
    private final FinanceDocumentStorage documentStorage;
    private final MembershipTerms membershipTerms;

    public ClubFinanceService(ClubRepository clubRepository,
                              ClubApprovalRequestRepository requestRepository,
                              ClubBudgetRepository budgetRepository,
                              ClubFinanceEntryRepository entryRepository,
                              ClubSponsorshipRepository sponsorshipRepository,
                              ClubAuthorizationService clubAuthorizationService,
                              ClubApprovalEngine approvalEngine,
                              FinanceDocumentStorage documentStorage,
                              MembershipTerms membershipTerms) {
        this.clubRepository = clubRepository;
        this.requestRepository = requestRepository;
        this.budgetRepository = budgetRepository;
        this.entryRepository = entryRepository;
        this.sponsorshipRepository = sponsorshipRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.approvalEngine = approvalEngine;
        this.documentStorage = documentStorage;
        this.membershipTerms = membershipTerms;
    }

    public ClubApprovalRequest submitBudget(UUID clubId, UUID userId, BudgetRequest request) {
        Club club = openClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_FINANCE);
        int currentYear = membershipTerms.currentAcademicYear();
        int academicYear = request.academicYear() != null ? request.academicYear() : currentYear;
        if (academicYear != currentYear && academicYear != currentYear + 1) {
            throw new BadRequestException("INVALID_ACADEMIC_YEAR", "Bütçe yalnızca bu akademik yıl veya bir sonraki için hazırlanabilir.");
        }
        if (requestRepository.existsByClubIdAndTypeAndStatusIn(clubId, ApprovalType.CLUB_BUDGET, ApprovalStatus.PENDING)) {
            throw new ConflictException("BUDGET_PENDING", "Kulübün onay bekleyen bir bütçesi var.");
        }
        Instant now = Instant.now();
        ClubApprovalRequest saved = requestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.CLUB_BUDGET,
                userId, null, null, null, request.note(), now));
        budgetRepository.save(new ClubBudget(clubId, saved.getId(), academicYear, request.plannedIncome(),
                request.plannedExpense(), request.description(), userId, now));
        return approvalEngine.submit(club, saved);
    }

    public ClubApprovalRequest submitEntry(UUID clubId, UUID userId, FinanceEntryForm form, MultipartFile document) {
        Club club = openClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_FINANCE);
        int academicYear = membershipTerms.academicYearOf(form.occurredOn());
        FinanceDocumentStorage.StoredDocument stored = store(document, clubId);
        Instant now = Instant.now();
        ClubApprovalRequest saved = requestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.CLUB_FINANCE_ENTRY,
                userId, null, null, null, form.note(), now));
        ClubFinanceEntry entry = new ClubFinanceEntry(clubId, saved.getId(), form.type(), form.amount(),
                form.description().strip(), form.occurredOn(), academicYear, userId, now);
        if (stored != null) {
            entry.attachDocument(stored.objectName(), stored.fileName());
        }
        if (form.type() == FinanceEntryType.EXPENSE) {
            entry.markBudgetExceeded(exceedsBudget(clubId, academicYear, form.amount()));
        }
        entryRepository.save(entry);
        return approvalEngine.submit(club, saved);
    }

    public ClubApprovalRequest submitSponsorship(UUID clubId, UUID userId, SponsorshipForm form, MultipartFile document) {
        Club club = openClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_SPONSORSHIP);
        boolean hasCash = form.cashAmount() != null && form.cashAmount().signum() > 0;
        boolean hasInKind = form.inKind() != null && !form.inKind().isBlank();
        if (!hasCash && !hasInKind) {
            throw new BadRequestException("SPONSORSHIP_EMPTY", "Nakit tutar veya ayni katkıdan en az biri girilmeli.");
        }
        if (form.endsOn() != null && form.endsOn().isBefore(form.startsOn())) {
            throw new BadRequestException("INVALID_PERIOD", "Bitiş tarihi başlangıçtan önce olamaz.");
        }
        FinanceDocumentStorage.StoredDocument stored = store(document, clubId);
        Instant now = Instant.now();
        ClubApprovalRequest saved = requestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.CLUB_SPONSORSHIP,
                userId, null, null, null, form.note(), now));
        ClubSponsorship sponsorship = new ClubSponsorship(clubId, saved.getId(), form.sponsorName().strip(),
                form.description().strip(), hasCash ? form.cashAmount() : null, hasInKind ? form.inKind().strip() : null,
                form.startsOn(), form.endsOn(), userId, now);
        if (stored != null) {
            sponsorship.attachDocument(stored.objectName(), stored.fileName());
        }
        sponsorshipRepository.save(sponsorship);
        return approvalEngine.submit(club, saved);
    }

    @Transactional(readOnly = true)
    public FinanceSummaryResponse summaryOf(UUID clubId, UUID userId, Integer year) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_FINANCE);
        int academicYear = year != null ? year : membershipTerms.currentAcademicYear();
        BudgetResponse budget = budgetRepository
                .findFirstByClubIdAndAcademicYearAndApprovedAtIsNotNullOrderByApprovedAtDesc(clubId, academicYear)
                .map(found -> BudgetResponse.of(found, ApprovalStatus.APPROVED))
                .orElse(null);
        BigDecimal income = entryRepository.approvedTotal(clubId, academicYear, FinanceEntryType.INCOME);
        BigDecimal expense = entryRepository.approvedTotal(clubId, academicYear, FinanceEntryType.EXPENSE);
        BigDecimal overall = entryRepository.approvedTotal(clubId, FinanceEntryType.INCOME)
                .subtract(entryRepository.approvedTotal(clubId, FinanceEntryType.EXPENSE));
        return new FinanceSummaryResponse(academicYear, AcademicYears.label(academicYear), budget, income, expense,
                income.subtract(expense), overall);
    }

    @Transactional(readOnly = true)
    public List<FinanceEntryResponse> entriesOf(UUID clubId, UUID userId, Integer year) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_FINANCE);
        int academicYear = year != null ? year : membershipTerms.currentAcademicYear();
        List<ClubFinanceEntry> entries = entryRepository.findByClubIdAndAcademicYearOrderByOccurredOnDescCreatedAtDesc(clubId, academicYear);
        Map<UUID, ApprovalStatus> statuses = statusesOf(entries.stream().map(ClubFinanceEntry::getRequestId).toList());
        return entries.stream().map(entry -> FinanceEntryResponse.of(entry, statuses.get(entry.getRequestId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> budgetsOf(UUID clubId, UUID userId) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_FINANCE);
        List<ClubBudget> budgets = budgetRepository.findByClubIdOrderByCreatedAtDesc(clubId);
        Map<UUID, ApprovalStatus> statuses = statusesOf(budgets.stream().map(ClubBudget::getRequestId).toList());
        return budgets.stream().map(budget -> BudgetResponse.of(budget, statuses.get(budget.getRequestId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<SponsorshipResponse> sponsorshipsOf(UUID clubId, UUID userId) {
        findClub(clubId);
        requireSponsorshipAccess(clubId, userId);
        List<ClubSponsorship> sponsorships = sponsorshipRepository.findByClubIdOrderByCreatedAtDesc(clubId);
        Map<UUID, ApprovalStatus> statuses = statusesOf(sponsorships.stream().map(ClubSponsorship::getRequestId).toList());
        return sponsorships.stream()
                .map(sponsorship -> SponsorshipResponse.of(sponsorship, statuses.get(sponsorship.getRequestId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Document entryDocument(UUID clubId, UUID entryId, UUID userId) {
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_FINANCE);
        ClubFinanceEntry entry = entryRepository.findById(entryId)
                .filter(found -> found.getClubId().equals(clubId) && found.getDocumentObject() != null)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Belge bulunamadı."));
        return new Document(documentStorage.open(entry.getDocumentObject()), entry.getDocumentName());
    }

    @Transactional(readOnly = true)
    public Document sponsorshipDocument(UUID clubId, UUID sponsorshipId, UUID userId) {
        requireSponsorshipAccess(clubId, userId);
        ClubSponsorship sponsorship = sponsorshipRepository.findById(sponsorshipId)
                .filter(found -> found.getClubId().equals(clubId) && found.getDocumentObject() != null)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Belge bulunamadı."));
        return new Document(documentStorage.open(sponsorship.getDocumentObject()), sponsorship.getDocumentName());
    }

    private boolean exceedsBudget(UUID clubId, int academicYear, BigDecimal amount) {
        return budgetRepository.findFirstByClubIdAndAcademicYearAndApprovedAtIsNotNullOrderByApprovedAtDesc(clubId, academicYear)
                .map(budget -> entryRepository.approvedTotal(clubId, academicYear, FinanceEntryType.EXPENSE)
                        .add(amount)
                        .compareTo(budget.getPlannedExpense()) > 0)
                .orElse(false);
    }

    private void requireSponsorshipAccess(UUID clubId, UUID userId) {
        ClubAccess access = clubAuthorizationService.accessOf(clubId, userId);
        if (!access.has(ClubPermission.VIEW_FINANCE) && !access.has(ClubPermission.PREPARE_SPONSORSHIP)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için kulüpte yetkiniz yok.");
        }
    }

    private FinanceDocumentStorage.StoredDocument store(MultipartFile document, UUID clubId) {
        if (document == null || document.isEmpty()) {
            return null;
        }
        return documentStorage.store(document, clubId);
    }

    private Map<UUID, ApprovalStatus> statusesOf(Collection<UUID> requestIds) {
        return requestRepository.findAllById(requestIds).stream()
                .collect(Collectors.toMap(ClubApprovalRequest::getId, ClubApprovalRequest::getStatus));
    }

    private Club openClub(UUID clubId) {
        Club club = findClub(clubId);
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulüpte mali kayıt açılamaz.");
        }
        return club;
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }

    public record Document(InputStream content, String fileName) {
    }
}
