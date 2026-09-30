package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.client.UserLookup;
import com.educonnect.clubservice.config.FoundingSettings;
import com.educonnect.clubservice.dto.response.ClubCreationRequestResponse;
import com.educonnect.clubservice.dto.response.FounderResponse;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;
import com.educonnect.clubservice.model.ClubFounder;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.FounderStatus;
import com.educonnect.clubservice.repository.ClubCreationRequestRepository;
import com.educonnect.clubservice.repository.ClubFounderRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClubFounderService {

    private static final String SUBJECT = "Kulüp kuruluş başvurusu";

    private final ClubFounderRepository founderRepository;
    private final ClubCreationRequestRepository requestRepository;
    private final ClubMembershipRepository membershipRepository;
    private final FoundingSettings foundingSettings;
    private final UserClient userClient;
    private final UserLookup userLookup;
    private final MembershipTerms membershipTerms;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubNotificationPublisher notificationPublisher;

    public ClubFounderService(ClubFounderRepository founderRepository,
                              ClubCreationRequestRepository requestRepository,
                              ClubMembershipRepository membershipRepository,
                              FoundingSettings foundingSettings,
                              UserClient userClient,
                              UserLookup userLookup,
                              MembershipTerms membershipTerms,
                              ClubCacheEvictor cacheEvictor,
                              ClubNotificationPublisher notificationPublisher) {
        this.founderRepository = founderRepository;
        this.requestRepository = requestRepository;
        this.membershipRepository = membershipRepository;
        this.foundingSettings = foundingSettings;
        this.userClient = userClient;
        this.userLookup = userLookup;
        this.membershipTerms = membershipTerms;
        this.cacheEvictor = cacheEvictor;
        this.notificationPublisher = notificationPublisher;
    }

    public Set<UUID> validateFounders(UUID requesterId, UUID advisorId, Collection<UUID> founderIds) {
        Set<UUID> invited = new LinkedHashSet<>(founderIds != null ? founderIds : Set.of());
        invited.remove(requesterId);
        if (invited.contains(advisorId)) {
            throw new BadRequestException("INVALID_FOUNDER", "Danışman kurucu üyelerden biri olamaz.");
        }
        int minMembers = foundingSettings.minMembers();
        if (invited.size() + 1 < minMembers) {
            throw new BadRequestException("FOUNDERS_REQUIRED", "Kulüp kurmak için en az " + minMembers + " kurucu öğrenci gerekir.");
        }
        if (!invited.isEmpty()) {
            Set<UUID> students = userClient.getUsersByIds(List.copyOf(invited)).stream()
                    .filter(user -> user != null && user.getId() != null && user.getStudentNumber() != null)
                    .map(UserSummary::getId)
                    .collect(Collectors.toSet());
            if (!students.containsAll(invited)) {
                throw new BadRequestException("INVALID_FOUNDER", "Kurucu üyeler kayıtlı öğrenciler arasından seçilmeli.");
            }
        }
        return invited;
    }

    public void registerFounders(ClubCreationRequest request, Set<UUID> invited) {
        founderRepository.save(ClubFounder.requester(request.getId(), request.getRequestingStudentId(), LocalDateTime.now()));
        invited.forEach(studentId -> {
            founderRepository.save(ClubFounder.invited(request.getId(), studentId));
            notificationPublisher.notifyUserAboutClubName(studentId, request.getClubName(), SUBJECT,
                    "\"" + request.getClubName() + "\" kulübünün kurucu üye listesine eklendiniz; katılımınızı onaylamanız bekleniyor.");
        });
        if (invited.isEmpty()) {
            sendToAdvisor(request);
        } else {
            request.setStatus(ClubCreationRequestStatus.PENDING_FOUNDERS);
            requestRepository.save(request);
        }
    }

    public ClubCreationRequestResponse respond(UUID requestId, UUID studentId, boolean confirmed) {
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Başvuru bulunamadı"));
        ClubFounder founder = founderRepository.findByRequestIdAndStudentId(requestId, studentId)
                .orElseThrow(() -> new NotFoundException("FOUNDER_NOT_FOUND", "Bu başvurunun kurucu listesinde değilsiniz."));
        if (request.getStatus() != ClubCreationRequestStatus.PENDING_FOUNDERS || founder.getStatus() != FounderStatus.INVITED) {
            throw new ConflictException("FOUNDER_RESPONDED", "Bu davet için yanıt verilemez.");
        }
        founder.respond(confirmed, LocalDateTime.now());
        founderRepository.save(founder);
        List<ClubFounder> founders = founderRepository.findByRequestId(requestId);
        long remaining = founders.stream().filter(found -> found.getStatus() != FounderStatus.DECLINED).count();
        if (remaining < foundingSettings.minMembers()) {
            request.setStatus(ClubCreationRequestStatus.REJECTED);
            request.setRejectionReason("Kurucu üye sayısı asgari sayının altına düştü.");
            request.setProcessedAt(LocalDateTime.now());
            requestRepository.save(request);
            notificationPublisher.notifyUserAboutClubName(request.getRequestingStudentId(), request.getClubName(), SUBJECT,
                    "\"" + request.getClubName() + "\" kuruluş başvurusu, kurucu üye sayısı asgari sayının altına düştüğü için kapandı.");
        } else if (founders.stream().noneMatch(found -> found.getStatus() == FounderStatus.INVITED)) {
            sendToAdvisor(request);
        }
        return responseOf(request, founders);
    }

    public void enrollFounders(ClubCreationRequest request, Club club) {
        LocalDateTime now = LocalDateTime.now();
        founderRepository.findByRequestId(request.getId()).stream()
                .filter(founder -> founder.getStatus() == FounderStatus.CONFIRMED)
                .filter(founder -> !founder.getStudentId().equals(request.getRequestingStudentId()))
                .forEach(founder -> {
                    ClubMembership membership = new ClubMembership(club.getId(), founder.getStudentId(), ClubPosition.MEMBER);
                    membership.reactivate(membershipTerms.currentValidUntil(), now);
                    membershipRepository.save(membership);
                    cacheEvictor.evictUser(founder.getStudentId());
                    notificationPublisher.notifyUser(founder.getStudentId(), club, SUBJECT,
                            "Kurucusu olduğunuz \"" + club.getName() + "\" kulübünün kuruluşu onaylandı.");
                });
    }

    @Transactional(readOnly = true)
    public List<ClubCreationRequestResponse> invitationsOf(UUID studentId) {
        List<UUID> requestIds = founderRepository.findByStudentIdAndStatus(studentId, FounderStatus.INVITED).stream()
                .map(ClubFounder::getRequestId)
                .toList();
        return responsesOf(requestRepository.findAllById(requestIds).stream()
                .filter(request -> request.getStatus() == ClubCreationRequestStatus.PENDING_FOUNDERS)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<ClubCreationRequestResponse> requestsOf(UUID studentId) {
        List<UUID> requestIds = founderRepository.findByStudentId(studentId).stream().map(ClubFounder::getRequestId).toList();
        return responsesOf(requestRepository.findAllById(requestIds).stream()
                .sorted((first, second) -> second.getRequestDate().compareTo(first.getRequestDate()))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<FounderResponse> foundersOfClub(UUID clubId) {
        return requestRepository.findFirstByClubId(clubId)
                .map(request -> namedFounders(founderRepository.findByRequestId(request.getId()).stream()
                        .filter(founder -> founder.getStatus() == FounderStatus.CONFIRMED)
                        .toList()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<ClubCreationRequestResponse> responsesOf(List<ClubCreationRequest> requests) {
        Map<UUID, List<ClubFounder>> founders = founderRepository
                .findByRequestIdIn(requests.stream().map(ClubCreationRequest::getId).toList()).stream()
                .collect(Collectors.groupingBy(ClubFounder::getRequestId));
        Map<UUID, UserSummary> users = userLookup.usersById(founders.values().stream()
                .flatMap(List::stream)
                .map(ClubFounder::getStudentId)
                .toList());
        return requests.stream()
                .map(request -> ClubCreationRequestResponse.from(request, founders.getOrDefault(request.getId(), List.of()).stream()
                        .map(founder -> FounderResponse.of(founder, users.get(founder.getStudentId())))
                        .toList()))
                .toList();
    }

    public ClubCreationRequestResponse responseOf(ClubCreationRequest request) {
        return responsesOf(List.of(request)).getFirst();
    }

    private ClubCreationRequestResponse responseOf(ClubCreationRequest request, List<ClubFounder> founders) {
        return ClubCreationRequestResponse.from(request, namedFounders(founders));
    }

    private List<FounderResponse> namedFounders(List<ClubFounder> founders) {
        Map<UUID, UserSummary> users = userLookup.usersById(founders.stream().map(ClubFounder::getStudentId).toList());
        return founders.stream().map(founder -> FounderResponse.of(founder, users.get(founder.getStudentId()))).toList();
    }

    private void sendToAdvisor(ClubCreationRequest request) {
        request.setStatus(ClubCreationRequestStatus.PENDING);
        requestRepository.save(request);
        notificationPublisher.notifyUserAboutClubName(request.getSuggestedAdvisorId(), request.getClubName(), SUBJECT,
                "\"" + request.getClubName() + "\" kulübü için danışmanlık onayınız bekleniyor.");
    }

}
