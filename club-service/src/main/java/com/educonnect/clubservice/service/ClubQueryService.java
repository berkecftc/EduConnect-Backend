package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.client.UserLookup;
import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.clubservice.dto.response.ArchivedClubDTO;
import com.educonnect.clubservice.dto.response.ClubAdminSummaryDto;
import com.educonnect.clubservice.dto.response.ClubCatalogEntry;
import com.educonnect.clubservice.dto.response.ClubDetailsDTO;
import com.educonnect.clubservice.dto.response.ClubSummaryDTO;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.dto.response.MyClubMembershipDTO;
import com.educonnect.clubservice.dto.response.PageResponse;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubNames;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.repository.ArchivedClubRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClubQueryService {

    private static final Logger log = LoggerFactory.getLogger(ClubQueryService.class);

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ArchivedClubRepository archivedClubRepository;
    private final UserClient userClient;
    private final UserLookup userLookup;
    private final ClubAuthorizationService clubAuthorizationService;

    public ClubQueryService(ClubRepository clubRepository,
                            ClubMembershipRepository membershipRepository,
                            ArchivedClubRepository archivedClubRepository,
                            UserClient userClient,
                            UserLookup userLookup,
                            ClubAuthorizationService clubAuthorizationService) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.archivedClubRepository = archivedClubRepository;
        this.userClient = userClient;
        this.userLookup = userLookup;
        this.clubAuthorizationService = clubAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<ClubSummaryDTO> getAllClubs() {
        return toClubSummaries(clubRepository.findByStatusNot(ClubStatus.CLOSED));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClubSummaryDTO> getClubsPage(int page, Integer size) {
        Page<Club> clubs = clubRepository.findByStatusNot(ClubStatus.CLOSED, PageResponse.request(page, size, Sort.by("name").and(Sort.by("id"))));
        return PageResponse.of(clubs, toClubSummaries(clubs.getContent()));
    }

    @Transactional(readOnly = true)
    public ClubDetailsDTO getClubDetails(UUID clubId, UUID viewerId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));

        List<ClubMembership> memberships = membershipRepository.findByClubId(clubId);
        boolean canViewAllMembers = clubAuthorizationService.accessOf(club, viewerId).has(ClubPermission.VIEW_MEMBERS);
        List<MemberDTO> memberDTOs = memberships.stream()
                .filter(ClubMembership::isActive)
                .filter(membership -> canViewAllMembers || membership.getClubRole().isManagement())
                .map(membership -> new MemberDTO(
                        membership.getStudentId(),
                        membership.getClubRole()
                ))
                .collect(Collectors.toList());

        long memberCount = membershipRepository.countByClubId(clubId);

        String advisorName = null;
        String advisorTitle = null;
        try {
            if (club.getAcademicAdvisorId() != null) {
                var advisor = userClient.getAcademicianById(club.getAcademicAdvisorId());
                if (advisor != null) {
                    advisorName = advisor.getFullName();
                    advisorTitle = advisor.getTitle();
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch advisor info for club {}: {}", clubId, e.getMessage());
        }

        ClubDetailsDTO detailsDTO = new ClubDetailsDTO();
        detailsDTO.setId(club.getId());
        detailsDTO.setName(club.getName());
        detailsDTO.setAbout(club.getAbout());
        detailsDTO.setLogoUrl(club.getLogoUrl());
        detailsDTO.setAcademicAdvisorId(club.getAcademicAdvisorId());
        detailsDTO.setAdvisorName(advisorName);
        detailsDTO.setAdvisorTitle(advisorTitle);
        detailsDTO.setMemberCount(memberCount);
        detailsDTO.setMembers(memberDTOs);
        detailsDTO.setStatus(club.getStatus());
        detailsDTO.setClosedAt(club.getClosedAt());
        detailsDTO.setClosureReason(club.getClosureReason());
        return detailsDTO;
    }

    @Transactional(readOnly = true)
    public List<UUID> getActiveMemberIds(UUID clubId) {
        return membershipRepository.findByClubId(clubId).stream()
                .filter(ClubMembership::isActive)
                .map(ClubMembership::getStudentId)
                .toList();
    }

    public List<ClubAdminSummaryDto> getAllClubsForAdmin() {
        List<Club> clubs = clubRepository.findAll();
        if (clubs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ClubMembership>> membershipsByClub = membershipRepository
                .findByClubIdIn(clubs.stream().map(Club::getId).toList()).stream()
                .collect(Collectors.groupingBy(ClubMembership::getClubId));
        Map<UUID, UUID> presidentByClub = new HashMap<>();
        membershipsByClub.forEach((clubId, memberships) -> memberships.stream()
                .filter(m -> m.getClubRole() == ClubPosition.PRESIDENT)
                .findFirst()
                .ifPresent(m -> presidentByClub.put(clubId, m.getStudentId())));
        Map<UUID, UserSummary> presidents = userLookup.usersById(presidentByClub.values());

        return clubs.stream().map(club -> {
            List<ClubMembership> memberships = membershipsByClub.getOrDefault(club.getId(), List.of());
            UUID presidentId = presidentByClub.get(club.getId());
            UserSummary president = presidentId != null ? presidents.get(presidentId) : null;
            String presidentName = president != null ? president.getFullName()
                    : presidentId != null ? presidentId.toString() : "Atanmamış";

            return new ClubAdminSummaryDto(
                    club.getId(),
                    club.getName(),
                    club.getLogoUrl(),
                    presidentName,
                    memberships.size()
            );
        }).collect(Collectors.toList());
    }

    public List<MemberDTO> getClubBoardMembers(UUID clubId) {
        if (!clubRepository.existsById(clubId)) {
            throw new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı");
        }

        List<ClubMembership> boardMembers = membershipRepository.findByClubId(clubId).stream()
                .filter(ClubMembership::isActive)
                .filter(m -> m.getClubRole().isManagement())
                .toList();
        Map<UUID, UserSummary> users = userLookup.usersById(boardMembers.stream().map(ClubMembership::getStudentId).toList());

        return boardMembers.stream()
                .map(m -> {
                    UserSummary user = users.get(m.getStudentId());
                    return new MemberDTO(
                            m.getStudentId(),
                            user != null ? user.getFirstName() : "Bilinmiyor",
                            user != null ? user.getLastName() : "User",
                            m.getClubRole().apiName()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MemberDTO> getPastPresidents(UUID clubId) {
        if (!clubRepository.existsById(clubId)) {
            throw new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı");
        }

        List<ClubMembership> pastPresidents = membershipRepository.findByClubId(clubId)
                .stream()
                .filter(m -> !m.isActive() && m.getTermEndDate() != null)
                .sorted((a, b) -> b.getTermStartDate().compareTo(a.getTermStartDate()))
                .toList();

        Map<UUID, UserSummary> users = userLookup.usersById(pastPresidents.stream().map(ClubMembership::getStudentId).toList());

        return pastPresidents.stream()
                .map(m -> {
                    UserSummary user = users.get(m.getStudentId());
                    return new MemberDTO(
                            m.getStudentId(),
                            user != null ? user.getFirstName() : "Bilinmiyor",
                            user != null ? user.getLastName() : "User",
                            "Geçmiş Başkan",
                            m.isActive(),
                            m.getTermStartDate(),
                            m.getTermEndDate()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ArchivedClubDTO> getAllArchivedClubs() {
        return archivedClubRepository.findAllByOrderByDeletedAtDesc().stream()
                .map(club -> new ArchivedClubDTO(
                        club.getArchiveId(),
                        club.getOriginalId(),
                        club.getName(),
                        club.getAbout(),
                        club.getLogoUrl(),
                        club.getAcademicAdvisorId(),
                        club.getDeletedAt(),
                        club.getDeletionReason(),
                        club.getDeletedByAdminId()
                ))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "studentClubMemberships", key = "#studentId")
    public List<MyClubMembershipDTO> getStudentClubMemberships(UUID studentId) {
        return toMembershipDtos(membershipRepository.findByStudentId(studentId));
    }

    @Cacheable(value = "managedClubs", key = "#userId")
    public List<MyClubMembershipDTO> getManagedClubs(UUID userId) {
        List<ClubMembership> memberships = membershipRepository.findByStudentId(userId).stream()
                .filter(membership -> membership.getClubRole().isManagement())
                .filter(ClubMembership::isActive)
                .toList();
        return toMembershipDtos(memberships).stream()
                .filter(dto -> dto.getClubStatus() != ClubStatus.CLOSED)
                .toList();
    }

    public boolean isStudentMemberOfClub(UUID clubId, UUID studentId) {
        return membershipRepository.existsByClubIdAndStudentIdAndIsActive(clubId, studentId, true);
    }

    @Transactional(readOnly = true)
    public UUID getClubAdvisorId(UUID clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı: " + clubId));
        return club.getAcademicAdvisorId();
    }

    @Transactional(readOnly = true)
    public UUID getClubIdByName(String name) {
        return clubRepository.findByNormalizedNameAndStatusNot(ClubNames.normalize(name), ClubStatus.CLOSED)
                .map(Club::getId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));
    }

    @Transactional(readOnly = true)
    public List<UUID> getClubIdsByAdvisorId(UUID advisorId) {
        return clubRepository.findByAcademicAdvisorId(advisorId).stream()
                .map(Club::getId)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClubCatalogEntry> getClubCatalog() {
        return clubRepository.findByStatusNot(ClubStatus.CLOSED, Sort.by("name")).stream()
                .map(club -> new ClubCatalogEntry(club.getId(), club.getName(), club.getAbout()))
                .toList();
    }

    private List<ClubSummaryDTO> toClubSummaries(List<Club> clubs) {
        if (clubs.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> memberCounts = membershipRepository.countByClubIds(clubs.stream().map(Club::getId).toList()).stream()
                .collect(Collectors.toMap(ClubMembershipRepository.ClubMemberCount::getClubId,
                        ClubMembershipRepository.ClubMemberCount::getTotal));
        Map<UUID, AcademicianSummary> advisors = userLookup.academiciansById(
                clubs.stream().map(Club::getAcademicAdvisorId).toList());

        return clubs.stream()
                .map(club -> {
                    AcademicianSummary advisor = advisors.get(club.getAcademicAdvisorId());
                    return new ClubSummaryDTO(
                            club.getId(),
                            club.getName(),
                            club.getLogoUrl(),
                            memberCounts.getOrDefault(club.getId(), 0L),
                            advisor != null ? advisor.getFullName() : null,
                            club.getAcademicAdvisorId()
                    );
                })
                .collect(Collectors.toList());
    }

    private List<MyClubMembershipDTO> toMembershipDtos(List<ClubMembership> memberships) {
        Map<UUID, Club> clubs = clubsById(memberships);
        return memberships.stream()
                .map(membership -> {
                    Club club = clubs.get(membership.getClubId());
                    if (club == null) {
                        return null;
                    }
                    MyClubMembershipDTO dto = new MyClubMembershipDTO();
                    dto.setClubId(club.getId());
                    dto.setClubName(club.getName());
                    dto.setLogoUrl(club.getLogoUrl());
                    dto.setClubRole(membership.getClubRole());
                    dto.setActive(membership.isActive());
                    dto.setTermStartDate(membership.getTermStartDate());
                    dto.setClubStatus(club.getStatus());
                    return dto;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<UUID, Club> clubsById(List<ClubMembership> memberships) {
        List<UUID> clubIds = memberships.stream().map(ClubMembership::getClubId).distinct().toList();
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        return clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));
    }
}
