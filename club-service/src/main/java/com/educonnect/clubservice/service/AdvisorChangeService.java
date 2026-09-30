package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.request.AdvisorChangeProposal;
import com.educonnect.clubservice.dto.response.AdvisorChangeRequestResponse;
import com.educonnect.clubservice.model.AdvisorChangeRequest;
import com.educonnect.clubservice.model.AdvisorChangeRequestStatus;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.repository.AdvisorChangeRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAccess;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdvisorChangeService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorChangeService.class);

    private final AdvisorChangeRequestRepository requestRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final AdvisorDirectory advisorDirectory;
    private final ClubLeadershipService leadershipService;
    private final ClubNotificationPublisher notificationPublisher;
    private final Clock clock;

    public AdvisorChangeService(AdvisorChangeRequestRepository requestRepository,
                                ClubRepository clubRepository,
                                ClubAuthorizationService clubAuthorizationService,
                                AdvisorDirectory advisorDirectory,
                                ClubLeadershipService leadershipService,
                                ClubNotificationPublisher notificationPublisher) {
        this.requestRepository = requestRepository;
        this.clubRepository = clubRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.advisorDirectory = advisorDirectory;
        this.leadershipService = leadershipService;
        this.notificationPublisher = notificationPublisher;
        this.clock = Clock.systemUTC();
    }

    public AdvisorChangeRequestResponse propose(UUID clubId, UUID requesterId, AdvisorChangeProposal proposal) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, requesterId, ClubPermission.PROPOSE_ADVISOR_CHANGE);
        UUID proposedAdvisorId = proposal.proposedAdvisorId();
        if (proposedAdvisorId.equals(club.getAcademicAdvisorId())) {
            throw new BadRequestException("SAME_ADVISOR", "Önerilen akademisyen zaten kulübün danışmanı.");
        }
        if (proposedAdvisorId.equals(requesterId)) {
            throw new BadRequestException("INVALID_ADVISOR", "Talep eden kişi danışman olarak önerilemez.");
        }
        advisorDirectory.requireAcademician(proposedAdvisorId);
        if (requestRepository.existsByClubIdAndStatus(clubId, AdvisorChangeRequestStatus.PENDING)) {
            throw new ConflictException("ADVISOR_CHANGE_PENDING", "Kulübün bekleyen bir danışman değişikliği talebi var.");
        }
        AdvisorChangeRequest request = requestRepository.save(new AdvisorChangeRequest(
                clubId, proposedAdvisorId, club.getAcademicAdvisorId(), requesterId, proposal.message()));
        log.info("Advisor change proposed: clubId={}, requestId={}", clubId, request.getId());
        notificationPublisher.notifyUser(proposedAdvisorId, club, "Kulüp danışmanlığı teklifi",
                "\"" + club.getName() + "\" kulübü sizi danışman akademisyen olarak öneriyor. "
                        + "Teklifi kabul edebilir veya reddedebilirsiniz.");
        return AdvisorChangeRequestResponse.of(request, club.getName());
    }

    @Transactional(readOnly = true)
    public List<AdvisorChangeRequestResponse> getClubRequests(UUID clubId, UUID userId) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_MANAGEMENT_DATA);
        return requestRepository.findByClubIdOrderByCreatedAtDesc(clubId).stream()
                .map(request -> AdvisorChangeRequestResponse.of(request, club.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvisorChangeRequestResponse> getPendingForAdvisor(UUID advisorId) {
        List<AdvisorChangeRequest> requests = requestRepository
                .findByProposedAdvisorIdAndStatusOrderByCreatedAtAsc(advisorId, AdvisorChangeRequestStatus.PENDING);
        Map<UUID, Club> clubs = clubsById(requests.stream().map(AdvisorChangeRequest::getClubId).toList());
        return requests.stream()
                .map(request -> AdvisorChangeRequestResponse.of(request,
                        clubs.containsKey(request.getClubId()) ? clubs.get(request.getClubId()).getName() : null))
                .toList();
    }

    public AdvisorChangeRequestResponse accept(UUID requestId, UUID advisorId) {
        AdvisorChangeRequest request = findPendingFor(requestId, advisorId);
        Club club = findClub(request.getClubId());
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulübün danışmanlığı kabul edilemez.");
        }
        UUID previousAdvisorId = club.getAcademicAdvisorId();
        club.setAcademicAdvisorId(advisorId);
        club.setStatus(ClubStatus.ACTIVE);
        clubRepository.save(club);
        request.decide(AdvisorChangeRequestStatus.ACCEPTED, null, clock.instant());
        requestRepository.save(request);
        log.info("Advisor change accepted: clubId={}, requestId={}", club.getId(), requestId);

        if (previousAdvisorId != null && !previousAdvisorId.equals(advisorId)) {
            notificationPublisher.notifyUser(previousAdvisorId, club, "Kulüp danışmanlığı",
                    "\"" + club.getName() + "\" kulübünün danışmanlığı yeni danışmana devredildi. Danışmanlık göreviniz sona erdi.");
        }
        notifyLeader(club, "Kulüp danışmanlığı",
                "\"" + club.getName() + "\" kulübü için önerdiğiniz akademisyen danışmanlığı kabul etti.");
        return AdvisorChangeRequestResponse.of(request, club.getName());
    }

    public AdvisorChangeRequestResponse reject(UUID requestId, UUID advisorId, String reason) {
        AdvisorChangeRequest request = findPendingFor(requestId, advisorId);
        Club club = findClub(request.getClubId());
        request.decide(AdvisorChangeRequestStatus.REJECTED, reason, clock.instant());
        requestRepository.save(request);
        log.info("Advisor change rejected: clubId={}, requestId={}", club.getId(), requestId);
        String message = "\"" + club.getName() + "\" kulübü için önerdiğiniz akademisyen danışmanlığı kabul etmedi.";
        if (reason != null && !reason.isBlank()) {
            message += " Neden: " + reason;
        }
        notifyLeader(club, "Kulüp danışmanlığı", message);
        return AdvisorChangeRequestResponse.of(request, club.getName());
    }

    public void cancel(UUID clubId, UUID requestId, UUID userId) {
        AdvisorChangeRequest request = requestRepository.findById(requestId)
                .filter(found -> found.getClubId().equals(clubId))
                .orElseThrow(() -> new NotFoundException("ADVISOR_CHANGE_NOT_FOUND", "Danışman değişikliği talebi bulunamadı."));
        ClubAccess access = clubAuthorizationService.accessOf(clubId, userId);
        if (!userId.equals(request.getRequestedBy()) && !access.has(ClubPermission.PROPOSE_ADVISOR_CHANGE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için kulüpte yetkiniz yok.");
        }
        if (!request.isPending()) {
            throw new ConflictException("ADVISOR_CHANGE_DECIDED", "Bu talep zaten sonuçlanmış.");
        }
        request.decide(AdvisorChangeRequestStatus.CANCELLED, null, clock.instant());
        requestRepository.save(request);
    }

    public void resign(UUID clubId, UUID advisorId, String reason) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, advisorId, ClubPermission.ADVISE);
        club.setAcademicAdvisorId(null);
        club.setStatus(ClubStatus.AWAITING_ADVISOR);
        clubRepository.save(club);
        log.info("Advisor resigned: clubId={}", clubId);
        String message = "\"" + club.getName() + "\" kulübünün danışmanı görevini bıraktı. "
                + "Kulüp yeni bir danışman kabul edene kadar danışman onayı gereken işler bekleyecek; yeni danışman önerebilirsiniz.";
        if (reason != null && !reason.isBlank()) {
            message += " Neden: " + reason;
        }
        notifyLeader(club, "Kulüp danışmanı ayrıldı", message);
    }

    public void advisorLeft(Collection<UUID> clubIds) {
        for (Club club : clubRepository.findAllById(clubIds)) {
            if (club.isClosed()) {
                continue;
            }
            notifyLeader(club, "Kulüp danışmanı ayrıldı",
                    "\"" + club.getName() + "\" kulübünün danışmanının hesabı kapandı. "
                            + "Kulüp yeni bir danışman kabul edene kadar danışman onayı gereken işler bekleyecek; yeni danışman önerebilirsiniz.");
        }
    }

    public void cancelPendingForClub(UUID clubId) {
        for (AdvisorChangeRequest request : requestRepository.findByClubIdAndStatus(clubId, AdvisorChangeRequestStatus.PENDING)) {
            request.decide(AdvisorChangeRequestStatus.CANCELLED, null, clock.instant());
            requestRepository.save(request);
        }
    }

    private void notifyLeader(Club club, String subject, String message) {
        leadershipService.currentLeaderOf(club.getId())
                .ifPresent(leaderId -> notificationPublisher.notifyUser(leaderId, club, subject, message));
    }

    private AdvisorChangeRequest findPendingFor(UUID requestId, UUID advisorId) {
        AdvisorChangeRequest request = requestRepository.findById(requestId)
                .filter(found -> found.getProposedAdvisorId().equals(advisorId))
                .orElseThrow(() -> new NotFoundException("ADVISOR_CHANGE_NOT_FOUND", "Danışman değişikliği talebi bulunamadı."));
        if (!request.isPending()) {
            throw new ConflictException("ADVISOR_CHANGE_DECIDED", "Bu talep zaten sonuçlanmış.");
        }
        return request;
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }

    private Map<UUID, Club> clubsById(List<UUID> clubIds) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        return clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));
    }
}
