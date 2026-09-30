package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.request.MeetingRequest;
import com.educonnect.clubservice.dto.response.DecisionBookEntryResponse;
import com.educonnect.clubservice.dto.response.MeetingResponse;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMeeting;
import com.educonnect.clubservice.model.ClubMeetingDecision;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubMeetingDecisionRepository;
import com.educonnect.clubservice.repository.ClubMeetingRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClubMeetingService {

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubApprovalRequestRepository requestRepository;
    private final ClubMeetingRepository meetingRepository;
    private final ClubMeetingDecisionRepository decisionRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubApprovalEngine approvalEngine;
    private final MembershipTerms membershipTerms;

    public ClubMeetingService(ClubRepository clubRepository,
                              ClubMembershipRepository membershipRepository,
                              ClubApprovalRequestRepository requestRepository,
                              ClubMeetingRepository meetingRepository,
                              ClubMeetingDecisionRepository decisionRepository,
                              ClubAuthorizationService clubAuthorizationService,
                              ClubApprovalEngine approvalEngine,
                              MembershipTerms membershipTerms) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.requestRepository = requestRepository;
        this.meetingRepository = meetingRepository;
        this.decisionRepository = decisionRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.approvalEngine = approvalEngine;
        this.membershipTerms = membershipTerms;
    }

    public ClubApprovalRequest submit(UUID clubId, UUID userId, MeetingRequest request) {
        Club club = findClub(clubId);
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulüpte tutanak hazırlanamaz.");
        }
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_MINUTES);
        List<ClubMembership> management = membershipRepository.findByClubId(clubId).stream()
                .filter(ClubMembership::isActive)
                .filter(membership -> membership.getClubRole().isManagement())
                .toList();
        Set<UUID> managementIds = management.stream().map(ClubMembership::getStudentId).collect(Collectors.toSet());
        if (!managementIds.containsAll(request.attendeeIds())) {
            throw new BadRequestException("INVALID_ATTENDEE", "Katılımcılar kulübün görevli üyeleri arasından seçilmeli.");
        }
        Set<UUID> boardIds = management.stream()
                .filter(membership -> membership.getClubRole().isBoard())
                .map(ClubMembership::getStudentId)
                .collect(Collectors.toSet());
        long boardPresent = request.attendeeIds().stream().filter(boardIds::contains).count();
        boolean quorumMet = boardPresent * 2 > boardIds.size();
        int academicYear = membershipTerms.academicYearOf(request.meetingAt().toLocalDate());
        Instant now = Instant.now();
        ClubApprovalRequest saved = requestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.CLUB_MEETING_MINUTES,
                userId, null, null, null, request.note(), now));
        ClubMeeting meeting = meetingRepository.save(new ClubMeeting(clubId, saved.getId(), request.meetingAt(), academicYear,
                blankToNull(request.location()), request.agenda().strip(), request.minutes().strip(), request.attendeeIds(),
                boardIds.size(), quorumMet, userId, now));
        List<String> decisions = request.decisions() != null ? request.decisions() : List.of();
        for (int i = 0; i < decisions.size(); i++) {
            decisionRepository.save(new ClubMeetingDecision(meeting.getId(), clubId, academicYear, i + 1, decisions.get(i).strip()));
        }
        return approvalEngine.submit(club, saved);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> meetingsOf(UUID clubId, UUID userId, Integer year) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_DECISIONS);
        int academicYear = membershipTerms.resolveAcademicYear(year);
        List<ClubMeeting> meetings = meetingRepository.findByClubIdAndAcademicYearOrderByMeetingAtDesc(clubId, academicYear);
        Map<UUID, List<ClubMeetingDecision>> decisions = decisionRepository
                .findByMeetingIdInOrderByItemOrder(meetings.stream().map(ClubMeeting::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ClubMeetingDecision::getMeetingId));
        Map<UUID, ApprovalStatus> statuses = requestRepository.findAllById(meetings.stream().map(ClubMeeting::getRequestId).toList())
                .stream()
                .collect(Collectors.toMap(ClubApprovalRequest::getId, ClubApprovalRequest::getStatus));
        return meetings.stream()
                .map(meeting -> MeetingResponse.of(meeting, decisions.getOrDefault(meeting.getId(), List.of()),
                        statuses.get(meeting.getRequestId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DecisionBookEntryResponse> decisionBookOf(UUID clubId, UUID userId, Integer year) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_DECISIONS);
        int academicYear = membershipTerms.resolveAcademicYear(year);
        List<ClubMeetingDecision> decisions = decisionRepository
                .findByClubIdAndAcademicYearAndDecisionNumberIsNotNullOrderByDecisionNumber(clubId, academicYear);
        Map<UUID, ClubMeeting> meetings = meetingRepository.findAllById(decisions.stream().map(ClubMeetingDecision::getMeetingId)
                        .distinct().toList())
                .stream()
                .collect(Collectors.toMap(ClubMeeting::getId, Function.identity()));
        return decisions.stream()
                .map(decision -> DecisionBookEntryResponse.of(decision, meetings.get(decision.getMeetingId())))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }
}
