package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserLookup;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.dto.response.PositionTermResponse;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubPositionTerm;
import com.educonnect.clubservice.repository.ClubPositionTermRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ClubPositionHistoryService {

    private final ClubRepository clubRepository;
    private final ClubPositionTermRepository termRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final UserLookup userLookup;

    public ClubPositionHistoryService(ClubRepository clubRepository,
                                      ClubPositionTermRepository termRepository,
                                      ClubAuthorizationService clubAuthorizationService,
                                      UserLookup userLookup) {
        this.clubRepository = clubRepository;
        this.termRepository = termRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.userLookup = userLookup;
    }

    public List<PositionTermResponse> historyOf(UUID clubId, UUID viewerId) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, viewerId, ClubPermission.VIEW_MEMBERS);
        return withNames(termRepository.findByClubIdOrderByStartedAtDesc(clubId), Map.of(clubId, club.getName()));
    }

    public List<PositionTermResponse> positionsOf(UUID studentId) {
        List<ClubPositionTerm> terms = termRepository.findByStudentIdOrderByStartedAtDesc(studentId);
        Map<UUID, String> clubNames = clubRepository.findAllById(terms.stream().map(ClubPositionTerm::getClubId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Club::getId, Club::getName));
        return withNames(terms, clubNames);
    }

    public List<MemberDTO> pastPresidents(UUID clubId) {
        findClub(clubId);
        List<ClubPositionTerm> terms = termRepository.findByClubIdAndPositionAndEndedAtIsNotNullOrderByEndedAtDesc(clubId,
                ClubPosition.PRESIDENT);
        Map<UUID, UserSummary> users = userLookup.usersById(terms.stream().map(ClubPositionTerm::getStudentId).distinct().toList());
        return terms.stream()
                .map(term -> {
                    UserSummary user = users.get(term.getStudentId());
                    return new MemberDTO(term.getStudentId(), user != null ? user.getFirstName() : null,
                            user != null ? user.getLastName() : null, ClubPosition.PRESIDENT.apiName(), false,
                            term.getStartedAt(), term.getEndedAt());
                })
                .toList();
    }

    private List<PositionTermResponse> withNames(List<ClubPositionTerm> terms, Map<UUID, String> clubNames) {
        Map<UUID, UserSummary> users = userLookup.usersById(terms.stream().map(ClubPositionTerm::getStudentId).distinct().toList());
        return terms.stream()
                .map(term -> PositionTermResponse.of(term, clubNames.get(term.getClubId()), users.get(term.getStudentId())))
                .toList();
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }
}
