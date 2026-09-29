package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.request.CreateClubRequest;
import com.educonnect.clubservice.dto.request.SubmitClubRequest;
import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
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
import java.util.UUID;

@Service
@Transactional
public class ClubFoundingService {

    private static final Logger log = LoggerFactory.getLogger(ClubFoundingService.class);

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubCreationRequestRepository requestRepository;
    private final UserClient userClient;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubNotificationPublisher notificationPublisher;

    public ClubFoundingService(ClubRepository clubRepository,
                               ClubMembershipRepository membershipRepository,
                               ClubCreationRequestRepository requestRepository,
                               UserClient userClient,
                               ClubAuthorizationService clubAuthorizationService,
                               ClubCacheEvictor cacheEvictor,
                               ClubManagementStatusPublisher managementStatusPublisher,
                               ClubNotificationPublisher notificationPublisher) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.requestRepository = requestRepository;
        this.userClient = userClient;
        this.clubAuthorizationService = clubAuthorizationService;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.notificationPublisher = notificationPublisher;
    }

    public Club createClub(CreateClubRequest request) {
        if (clubRepository.findByName(request.getName()).isPresent()) {
            throw new ConflictException("CLUB_NAME_TAKEN", "Club with this name already exists.");
        }
        if (request.getClubPresidentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kulüp başkanı zorunludur.");
        }
        ensureValidAdvisor(request.getAcademicAdvisorId());
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
        ensureValidAdvisor(request.getAcademicAdvisorId());
        ensureEligibleForManagement(studentId);
        if (requestRepository.existsByRequestingStudentIdAndStatus(studentId, ClubCreationRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bekleyen bir kulüp kuruluş başvurunuz zaten var.");
        }
        if (clubRepository.findByName(request.getName()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu isimde bir kulüp zaten var.");
        }

        ClubCreationRequest newRequest = new ClubCreationRequest();
        newRequest.setClubName(request.getName());
        newRequest.setAbout(request.getAbout());
        newRequest.setSuggestedAdvisorId(request.getAcademicAdvisorId());
        newRequest.setRequestingStudentId(studentId);

        ClubCreationRequest saved = requestRepository.save(newRequest);
        notificationPublisher.notifyUserAboutClubName(request.getAcademicAdvisorId(), request.getName(),
                "Kulüp kuruluş başvurusu",
                "\"" + request.getName() + "\" kulübü için danışmanlık onayınız bekleniyor.");
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
        requestRepository.save(request);

        notificationPublisher.notifyUser(request.getRequestingStudentId(), newClub, "Kulüp kuruluş başvurusu",
                "\"" + newClub.getName() + "\" kulübünün kuruluşu onaylandı. Kulüp başkanı olarak atandınız.");
        return newClub;
    }

    private void ensureValidAdvisor(UUID advisorId) {
        if (advisorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kulübün bir danışman akademisyeni olmalıdır.");
        }
        AcademicianSummary advisor;
        try {
            advisor = userClient.getAcademicianById(advisorId);
        } catch (Exception e) {
            log.warn("Advisor lookup failed for {}: {}", advisorId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danışman akademisyen bulunamadı.");
        }
        if (advisor == null || !"Academician".equalsIgnoreCase(advisor.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danışman olarak yalnızca bir akademisyen seçilebilir.");
        }
    }

    private void ensureEligibleForManagement(UUID studentId) {
        if (clubAuthorizationService.activeManagementPositionOf(studentId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu öğrencinin başka bir kulüpte yönetim görevi var. Bir öğrenci yalnızca bir kulüpte yönetim görevi alabilir.");
        }
    }
}
