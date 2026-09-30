package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserLookup;
import com.educonnect.clubservice.dto.request.OpenElectionRequest;
import com.educonnect.clubservice.dto.response.ElectionResponse;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubElection;
import com.educonnect.clubservice.model.ClubElectionBallotCast;
import com.educonnect.clubservice.model.ClubElectionCandidate;
import com.educonnect.clubservice.model.ClubElectionVote;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.model.ElectionBallot;
import com.educonnect.clubservice.model.ElectionStatus;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubElectionBallotCastRepository;
import com.educonnect.clubservice.repository.ClubElectionCandidateRepository;
import com.educonnect.clubservice.repository.ClubElectionRepository;
import com.educonnect.clubservice.repository.ClubElectionVoteRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClubElectionService {

    private static final Set<ElectionStatus> OPEN = EnumSet.of(ElectionStatus.CANDIDACY, ElectionStatus.VOTING,
            ElectionStatus.AWAITING_APPROVAL);

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubElectionRepository electionRepository;
    private final ClubElectionCandidateRepository candidateRepository;
    private final ClubElectionBallotCastRepository castRepository;
    private final ClubElectionVoteRepository voteRepository;
    private final ClubApprovalRequestRepository requestRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubApprovalEngine approvalEngine;
    private final ClubDecisionLog decisionLog;
    private final ClubNotificationPublisher notificationPublisher;
    private final UserLookup userLookup;

    public ClubElectionService(ClubRepository clubRepository,
                               ClubMembershipRepository membershipRepository,
                               ClubElectionRepository electionRepository,
                               ClubElectionCandidateRepository candidateRepository,
                               ClubElectionBallotCastRepository castRepository,
                               ClubElectionVoteRepository voteRepository,
                               ClubApprovalRequestRepository requestRepository,
                               ClubAuthorizationService clubAuthorizationService,
                               ClubApprovalEngine approvalEngine,
                               ClubDecisionLog decisionLog,
                               ClubNotificationPublisher notificationPublisher,
                               UserLookup userLookup) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.castRepository = castRepository;
        this.voteRepository = voteRepository;
        this.requestRepository = requestRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.approvalEngine = approvalEngine;
        this.decisionLog = decisionLog;
        this.notificationPublisher = notificationPublisher;
        this.userLookup = userLookup;
    }

    public ElectionResponse open(UUID clubId, UUID userId, OpenElectionRequest request) {
        Club club = openClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_ELECTION);
        if (electionRepository.existsByClubIdAndStatusIn(clubId, OPEN)) {
            throw new ConflictException("ELECTION_OPEN", "Kulübün devam eden bir seçimi var.");
        }
        ClubElection election = electionRepository.save(new ClubElection(clubId, request.boardSeats(), request.auditSeats(),
                request.note(), userId, Instant.now()));
        decisionLog.record(clubId, DecisionAction.ELECTION_OPENED, userId, null, request.note());
        notifyMembers(club, "Genel kurul seçimi açıldı; başkanlık, yönetim kurulu ve denetim kurulu için aday olabilirsiniz.");
        return responseOf(election, userId);
    }

    public ElectionResponse nominate(UUID clubId, UUID electionId, UUID userId, ElectionBallot ballot) {
        ClubElection election = election(clubId, electionId, ElectionStatus.CANDIDACY);
        requireActiveMember(clubId, userId);
        if (election.seatsOf(ballot) == 0) {
            throw new BadRequestException("NO_SEATS", "Bu seçimde bu kurul için koltuk yok.");
        }
        if (candidateRepository.findByElectionIdAndStudentId(electionId, userId).isPresent()) {
            throw new ConflictException("ALREADY_CANDIDATE", "Bu seçimde zaten adaysınız.");
        }
        clubAuthorizationService.activeManagementPositionOf(userId)
                .filter(membership -> !membership.getClubId().equals(clubId))
                .ifPresent(membership -> {
                    throw new ConflictException("MANAGEMENT_ELSEWHERE",
                            "Başka bir kulüpte yönetim görevi olan öğrenci aday olamaz.");
                });
        candidateRepository.save(new ClubElectionCandidate(electionId, ballot, userId, Instant.now()));
        return responseOf(election, userId);
    }

    public ElectionResponse withdraw(UUID clubId, UUID electionId, UUID userId) {
        ClubElection election = election(clubId, electionId, ElectionStatus.CANDIDACY);
        ClubElectionCandidate candidate = candidateRepository.findByElectionIdAndStudentId(electionId, userId)
                .orElseThrow(() -> new NotFoundException("CANDIDATE_NOT_FOUND", "Bu seçimde aday değilsiniz."));
        candidateRepository.delete(candidate);
        return responseOf(election, userId);
    }

    public ElectionResponse startVoting(UUID clubId, UUID electionId, UUID userId) {
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_ELECTION);
        ClubElection election = election(clubId, electionId, ElectionStatus.CANDIDACY);
        boolean hasPresidentCandidate = candidateRepository.findByElectionIdOrderByCreatedAt(electionId).stream()
                .anyMatch(candidate -> candidate.getBallot() == ElectionBallot.PRESIDENT);
        if (!hasPresidentCandidate) {
            throw new BadRequestException("NO_PRESIDENT_CANDIDATE", "Başkanlık için en az bir aday olmadan oylama başlatılamaz.");
        }
        election.startVoting(Instant.now());
        electionRepository.save(election);
        decisionLog.record(clubId, DecisionAction.ELECTION_VOTING_STARTED, userId, null, null);
        notifyMembers(findClub(clubId), "Genel kurul seçiminde oylama başladı; oyunuzu kullanabilirsiniz.");
        return responseOf(election, userId);
    }

    public ElectionResponse vote(UUID clubId, UUID electionId, UUID userId, ElectionBallot ballot, Set<UUID> candidateIds) {
        ClubElection election = election(clubId, electionId, ElectionStatus.VOTING);
        requireActiveMember(clubId, userId);
        if (castRepository.existsByElectionIdAndBallotAndVoterId(electionId, ballot, userId)) {
            throw new ConflictException("ALREADY_VOTED", "Bu oylamada oyunuzu zaten kullandınız.");
        }
        if (candidateIds.size() > election.seatsOf(ballot)) {
            throw new BadRequestException("TOO_MANY_VOTES", "Koltuk sayısından fazla adaya oy verilemez.");
        }
        Set<UUID> ballotCandidates = candidateRepository.findByElectionIdOrderByCreatedAt(electionId).stream()
                .filter(candidate -> candidate.getBallot() == ballot)
                .map(ClubElectionCandidate::getId)
                .collect(Collectors.toSet());
        if (!ballotCandidates.containsAll(candidateIds)) {
            throw new BadRequestException("INVALID_CANDIDATE", "Oy yalnızca bu oylamanın adaylarına verilebilir.");
        }
        castRepository.save(new ClubElectionBallotCast(electionId, ballot, userId, Instant.now()));
        candidateIds.forEach(candidateId -> voteRepository.save(new ClubElectionVote(electionId, candidateId)));
        return responseOf(election, userId);
    }

    public ClubApprovalRequest close(UUID clubId, UUID electionId, UUID userId) {
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_ELECTION);
        Club club = findClub(clubId);
        ClubElection election = election(clubId, electionId, ElectionStatus.VOTING);
        Map<UUID, Long> tally = voteRepository.findByElectionId(electionId).stream()
                .collect(Collectors.groupingBy(ClubElectionVote::getCandidateId, Collectors.counting()));
        List<ClubElectionCandidate> candidates = candidateRepository.findByElectionIdOrderByCreatedAt(electionId);
        for (ElectionBallot ballot : ElectionBallot.values()) {
            List<ClubElectionCandidate> ranked = candidates.stream()
                    .filter(candidate -> candidate.getBallot() == ballot)
                    .sorted(Comparator.comparing((ClubElectionCandidate candidate) -> tally.getOrDefault(candidate.getId(), 0L))
                            .reversed()
                            .thenComparing(ClubElectionCandidate::getCreatedAt))
                    .toList();
            for (int i = 0; i < ranked.size(); i++) {
                ClubElectionCandidate candidate = ranked.get(i);
                int votes = tally.getOrDefault(candidate.getId(), 0L).intValue();
                candidate.count(votes, i < election.seatsOf(ballot) && votes > 0);
            }
        }
        candidateRepository.saveAll(candidates);
        int eligible = (int) membershipRepository.countByClubIdAndIsActive(clubId, true);
        int voters = (int) castRepository.countVoters(electionId);
        Instant now = Instant.now();
        ClubApprovalRequest request = requestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.CLUB_ELECTION, userId,
                null, null, null, "Katılım: " + voters + "/" + eligible, now));
        election.close(eligible, voters, request.getId(), now);
        electionRepository.save(election);
        return approvalEngine.submit(club, request);
    }

    public ElectionResponse cancel(UUID clubId, UUID electionId, UUID userId) {
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_ELECTION);
        ClubElection election = findElection(clubId, electionId);
        if (election.getStatus() != ElectionStatus.CANDIDACY && election.getStatus() != ElectionStatus.VOTING) {
            throw new ConflictException("ELECTION_LOCKED", "Oylaması kapanmış seçim bu yolla iptal edilemez.");
        }
        election.cancel(Instant.now());
        electionRepository.save(election);
        decisionLog.record(clubId, DecisionAction.ELECTION_CANCELLED, userId, null, null);
        return responseOf(election, userId);
    }

    @Transactional(readOnly = true)
    public List<ElectionResponse> electionsOf(UUID clubId, UUID userId) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_MEMBERS);
        return electionRepository.findByClubIdOrderByOpenedAtDesc(clubId).stream()
                .map(election -> responseOf(election, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ElectionResponse electionOf(UUID clubId, UUID electionId, UUID userId) {
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_MEMBERS);
        return responseOf(findElection(clubId, electionId), userId);
    }

    public ElectionResponse responseOf(ClubElection election, UUID viewerId) {
        List<ClubElectionCandidate> candidates = candidateRepository.findByElectionIdOrderByCreatedAt(election.getId());
        Set<ElectionBallot> myBallots = viewerId == null ? null : castRepository.findByElectionIdAndVoterId(election.getId(), viewerId)
                .stream()
                .map(ClubElectionBallotCast::getBallot)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ElectionBallot.class)));
        return ElectionResponse.of(election, candidates,
                userLookup.usersById(candidates.stream().map(ClubElectionCandidate::getStudentId).toList()), myBallots);
    }

    private void requireActiveMember(UUID clubId, UUID userId) {
        membershipRepository.findByClubIdAndStudentId(clubId, userId)
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Yalnızca kulübün aktif üyeleri katılabilir."));
    }

    private void notifyMembers(Club club, String message) {
        membershipRepository.findByClubId(club.getId()).stream()
                .filter(ClubMembership::isActive)
                .map(ClubMembership::getStudentId)
                .forEach(memberId -> notificationPublisher.notifyUser(memberId, club, "Genel kurul seçimi", message));
    }

    private ClubElection election(UUID clubId, UUID electionId, ElectionStatus expected) {
        openClub(clubId);
        ClubElection election = findElection(clubId, electionId);
        if (election.getStatus() != expected) {
            throw new ConflictException("ELECTION_PHASE", "Seçim bu işlem için uygun aşamada değil.");
        }
        return election;
    }

    private ClubElection findElection(UUID clubId, UUID electionId) {
        return electionRepository.findById(electionId)
                .filter(election -> election.getClubId().equals(clubId))
                .orElseThrow(() -> new NotFoundException("ELECTION_NOT_FOUND", "Seçim bulunamadı."));
    }

    private Club openClub(UUID clubId) {
        Club club = findClub(clubId);
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulüpte seçim yapılamaz.");
        }
        return club;
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }

}
