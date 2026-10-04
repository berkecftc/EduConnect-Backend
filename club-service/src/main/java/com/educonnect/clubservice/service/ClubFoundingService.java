package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.request.CreateClubRequest;
import com.educonnect.clubservice.dto.request.SubmitClubRequest;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;
import com.educonnect.clubservice.model.ClubNames;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.repository.ClubCreationRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ClubFoundingService {

    private static final List<ClubCreationRequestStatus> OPEN_STATUSES =
            List.of(ClubCreationRequestStatus.PENDING, ClubCreationRequestStatus.PENDING_FOUNDERS);

    private static final Logger log = LoggerFactory.getLogger(ClubFoundingService.class);

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubCreationRequestRepository requestRepository;
    private final AdvisorDirectory advisorDirectory;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubNotificationPublisher notificationPublisher;
    private final ClubDecisionLog decisionLog;
    private final ClubFounderService founderService;

    public ClubFoundingService(ClubRepository clubRepository,
                               ClubMembershipRepository membershipRepository,
                               ClubCreationRequestRepository requestRepository,
                               AdvisorDirectory advisorDirectory,
                               ClubAuthorizationService clubAuthorizationService,
                               ClubCacheEvictor cacheEvictor,
                               ClubManagementStatusPublisher managementStatusPublisher,
                               ClubNotificationPublisher notificationPublisher,
                               ClubDecisionLog decisionLog,
                               ClubFounderService founderService) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.requestRepository = requestRepository;
        this.advisorDirectory = advisorDirectory;
        this.clubAuthorizationService = clubAuthorizationService;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.notificationPublisher = notificationPublisher;
        this.decisionLog = decisionLog;
        this.founderService = founderService;
    }

    public Club createClub(CreateClubRequest request) {
        if (clubRepository.existsByNormalizedNameAndStatusNot(ClubNames.normalize(request.getName()), ClubStatus.CLOSED)) {
            throw new ConflictException("CLUB_NAME_TAKEN", "Bu isimde bir kulüp zaten var.");
        }
        if (request.getClubPresidentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kulüp başkanı zorunludur.");
        }
        advisorDirectory.requireAcademician(request.getAcademicAdvisorId());
        ensureEligibleForManagement(request.getClubPresidentId());

        Club newClub = new Club();
        newClub.setName(request.getName());
        newClub.setAbout(request.getAbout());
        newClub.setAcademicAdvisorId(request.getAcademicAdvisorId());
        Club savedClub = clubRepository.save(newClub);

        ClubMembership presidentMembership = new ClubMembership(
                savedClub.getId(),
                request.getClubPresidentId(),
                ClubPosition.PRESIDENT
        );
        presidentMembership.setActive(true);
        presidentMembership.setTermStartDate(LocalDateTime.now());
        membershipRepository.save(presidentMembership);
        cacheEvictor.evictUser(request.getClubPresidentId());
        managementStatusPublisher.publishCurrentStatus(request.getClubPresidentId());
        notificationPublisher.notifyUser(request.getClubPresidentId(), savedClub, "Kulüp başkanlığı",
                "\"" + savedClub.getName() + "\" kulübü kuruldu ve kulüp başkanı olarak atandınız.");
        notificationPublisher.notifyAdvisor(savedClub, "Kulüp danışmanlığı",
                "\"" + savedClub.getName() + "\" kulübü kuruldu ve akademik danışman olarak atandınız.");

        return savedClub;
    }

    public ClubCreationRequest submitClubCreationRequest(SubmitClubRequest request, UUID studentId, boolean requesterIsStudent) {
        if (!requesterIsStudent) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kulüp kuruluş başvurusunu yalnızca öğrenciler yapabilir.");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kulüp adı zorunludur.");
        }
        if (studentId.equals(request.getAcademicAdvisorId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Başvuru sahibi kulübün danışmanı olamaz.");
        }
        advisorDirectory.requireAcademician(request.getAcademicAdvisorId());
        ensureEligibleForManagement(studentId);
        if (requestRepository.existsByRequestingStudentIdAndStatusIn(studentId, OPEN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bekleyen bir kulüp kuruluş başvurunuz zaten var.");
        }
        String normalizedName = ClubNames.normalize(request.getName());
        if (clubRepository.existsByNormalizedNameAndStatusNot(normalizedName, ClubStatus.CLOSED)) {
            throw new ConflictException("CLUB_NAME_TAKEN", "Bu isimde bir kulüp zaten var.");
        }
        boolean pendingWithSameName = requestRepository.findByStatusIn(OPEN_STATUSES).stream()
                .anyMatch(pending -> normalizedName.equals(ClubNames.normalize(pending.getClubName())));
        if (pendingWithSameName) {
            throw new ConflictException("CLUB_NAME_TAKEN", "Bu isimde bekleyen bir kulüp kuruluş başvurusu var.");
        }

        Set<UUID> invitedFounders = founderService.validateFounders(studentId, request.getAcademicAdvisorId(), request.getFounderIds());

        ClubCreationRequest newRequest = new ClubCreationRequest();
        newRequest.setClubName(request.getName());
        newRequest.setAbout(request.getAbout());
        newRequest.setSuggestedAdvisorId(request.getAcademicAdvisorId());
        newRequest.setRequestingStudentId(studentId);

        ClubCreationRequest saved = requestRepository.save(newRequest);
        founderService.registerFounders(saved, invitedFounders);
        return saved;
    }

    public Club approveClubCreationRequest(UUID requestId) {
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı"));
        return approveCreationRequest(request, null);
    }

    public List<ClubCreationRequest> getPendingCreationRequestsForAdvisor(UUID advisorId) {
        return requestRepository.findByStatusAndSuggestedAdvisorId(ClubCreationRequestStatus.PENDING, advisorId);
    }

    public Club approveClubCreationRequestByAdvisor(UUID requestId, UUID advisorId) {
        ClubCreationRequest request = findCreationRequestForAdvisor(requestId, advisorId);
        return approveCreationRequest(request, advisorId);
    }

    public ClubCreationRequest rejectClubCreationRequestByAdvisor(UUID requestId, UUID advisorId, String reason) {
        ClubCreationRequest request = findCreationRequestForAdvisor(requestId, advisorId);
        request.setStatus(ClubCreationRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(advisorId);
        ClubCreationRequest saved = requestRepository.save(request);

        String message = "\"" + request.getClubName() + "\" kulübü için kuruluş başvurunuz danışman tarafından reddedildi.";
        if (reason != null && !reason.isBlank()) {
            message += " Neden: " + reason;
        }
        notificationPublisher.notifyUserAboutClubName(request.getRequestingStudentId(), request.getClubName(),
                "Kulüp kuruluş başvurusu", message);
        return saved;
    }

    public List<ClubCreationRequest> getPendingClubRequests() {
        return requestRepository.findByStatus(ClubCreationRequestStatus.PENDING);
    }

    public void rejectClubCreationRequest(UUID requestId) {
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "İstek bulunamadı"));

        request.setStatus(ClubCreationRequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        requestRepository.save(request);
        notificationPublisher.notifyUserAboutClubName(request.getRequestingStudentId(), request.getClubName(),
                "Kulüp kuruluş başvurusu",
                "\"" + request.getClubName() + "\" kulübü için kuruluş başvurunuz reddedildi.");
    }

    private ClubCreationRequest findCreationRequestForAdvisor(UUID requestId, UUID advisorId) {
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı"));
        if (!advisorId.equals(request.getSuggestedAdvisorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu başvurunun önerilen danışmanı değilsiniz.");
        }
        return request;
    }

    private Club approveCreationRequest(ClubCreationRequest request, UUID approverId) {
        if (request.getStatus() != ClubCreationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu başvuru zaten işlenmiş.");
        }
        if (request.getRequestingStudentId().equals(request.getSuggestedAdvisorId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Başvuru sahibi kendi kulübünün danışmanı olamaz.");
        }

        CreateClubRequest createDto = new CreateClubRequest();
        createDto.setName(request.getClubName());
        createDto.setAbout(request.getAbout());
        createDto.setAcademicAdvisorId(request.getSuggestedAdvisorId());
        createDto.setClubPresidentId(request.getRequestingStudentId());

        Club newClub = createClub(createDto);

        request.setStatus(ClubCreationRequestStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(approverId);
        request.setClubId(newClub.getId());
        requestRepository.save(request);
        decisionLog.record(newClub.getId(), DecisionAction.CLUB_FOUNDED, approverId, request.getRequestingStudentId(), null);
        founderService.enrollFounders(request, newClub);

        notificationPublisher.notifyUser(request.getRequestingStudentId(), newClub, "Kulüp kuruluş başvurusu",
                "\"" + newClub.getName() + "\" kulübünün kuruluşu onaylandı. Kulüp başkanı olarak atandınız.");
        return newClub;
    }


    private void ensureEligibleForManagement(UUID studentId) {
        if (clubAuthorizationService.activeManagementPositionOf(studentId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu öğrencinin başka bir kulüpte yönetim görevi var. Bir öğrenci yalnızca bir kulüpte yönetim görevi alabilir.");
        }
    }
}
