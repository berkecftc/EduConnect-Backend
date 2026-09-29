package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.request.CreateRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.RoleChangeRequest;
import com.educonnect.clubservice.model.RoleChangeRequestStatus;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.repository.RoleChangeRequestRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RoleChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(RoleChangeRequestService.class);

    private final RoleChangeRequestRepository roleChangeRequestRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final UserClient userClient;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubPositionRules positionRules;
    private final RoleChangeNotifier notifier;
    private final RoleChangeRequestMapper mapper;

    public RoleChangeRequestService(RoleChangeRequestRepository roleChangeRequestRepository,
                                    ClubMembershipRepository membershipRepository,
                                    ClubRepository clubRepository,
                                    UserClient userClient,
                                    ClubAuthorizationService clubAuthorizationService,
                                    ClubPositionRules positionRules,
                                    RoleChangeNotifier notifier,
                                    RoleChangeRequestMapper mapper) {
        this.roleChangeRequestRepository = roleChangeRequestRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.userClient = userClient;
        this.clubAuthorizationService = clubAuthorizationService;
        this.positionRules = positionRules;
        this.notifier = notifier;
        this.mapper = mapper;
    }

    public RoleChangeRequestDTO createRoleChangeRequest(UUID clubId, CreateRoleChangeRequestDTO dto, UUID requesterId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
        clubAuthorizationService.require(clubId, requesterId, ClubPermission.PROPOSE_POSITION_CHANGE);

        ClubPosition requestedRole = dto.getRequestedRole();
        if (requestedRole == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Talep edilen görev zorunludur.");
        }
        UUID studentId = resolveStudentId(dto);
        return submitRequest(club, studentId, requestedRole, requesterId);
    }

    public RoleChangeRequestDTO requestRoleRevocation(UUID clubId, UUID studentId, UUID requesterId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
        clubAuthorizationService.require(clubId, requesterId, ClubPermission.PROPOSE_POSITION_CHANGE);

        if (studentId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kendinizi görevden alamazsınız.");
        }
        return submitRequest(club, studentId, ClubPosition.MEMBER, requesterId);
    }

    @Transactional(readOnly = true)
    public List<RoleChangeRequestDTO> getClubRoleChangeRequests(UUID clubId, UUID requesterId) {
        clubAuthorizationService.require(clubId, requesterId, ClubPermission.VIEW_MANAGEMENT_DATA);

        Club club = clubRepository.findById(clubId).orElse(null);
        List<RoleChangeRequest> requests = roleChangeRequestRepository.findByClubId(clubId);
        return mapper.toDtos(requests, request -> club);
    }

    private RoleChangeRequestDTO submitRequest(Club club, UUID studentId, ClubPosition requestedRole, UUID requesterId) {
        UUID clubId = club.getId();
        ClubMembership studentMembership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Bu öğrenci kulübün aktif üyesi değil. Önce üye olmalı."));

        ClubPosition currentRole = studentMembership.getClubRole();
        if (currentRole == requestedRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Öğrenci zaten bu görevde.");
        }
        if (currentRole == ClubPosition.PRESIDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Başkanın görevi yalnızca danışman kararıyla değiştirilebilir.");
        }

        if (roleChangeRequestRepository.existsByClubIdAndStudentIdAndStatus(
                clubId, studentId, RoleChangeRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu öğrenci için zaten bekleyen bir görev değişikliği talebi var.");
        }

        if (requestedRole.isManagement()) {
            positionRules.ensureCapacity(clubId, requestedRole, true);
            positionRules.ensureNoManagementPositionElsewhere(studentId, clubId);
        }

        RoleChangeRequest request = new RoleChangeRequest(clubId, studentId, currentRole, requestedRole, requesterId);
        RoleChangeRequest savedRequest = roleChangeRequestRepository.save(request);

        log.info("Role change request created: clubId={}, studentId={}, from={}, to={}, requesterId={}",
                clubId, studentId, currentRole, requestedRole, requesterId);

        notifier.notifyAdvisor(club, savedRequest,
                requestedRole == ClubPosition.MEMBER
                        ? "Yeni bir görevden alma talebi onayınızı bekliyor."
                        : "Yeni görev değişikliği talebi onayınızı bekliyor.");

        return mapper.toDto(savedRequest, club);
    }

    private UUID resolveStudentId(CreateRoleChangeRequestDTO dto) {
        if (!dto.hasStudentIdentifier()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Öğrenci ID veya öğrenci numarası zorunludur.");
        }

        if (dto.hasStudentId()) {
            try {
                return UUID.fromString(dto.getStudentId().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Geçersiz öğrenci ID formatı: " + dto.getStudentId());
            }
        }

        return resolveStudentIdByStudentNumber(dto.getStudentNumber().trim());
    }

    private UUID resolveStudentIdByStudentNumber(String studentNumber) {
        if (studentNumber == null || studentNumber.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Geçersiz öğrenci numarası formatı.");
        }

        try {
            UserSummary userSummary = userClient.getUserByStudentNumber(studentNumber);

            if (userSummary == null || userSummary.getId() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bu öğrenci numarasına sahip kullanıcı bulunamadı: " + studentNumber);
            }
            return userSummary.getId();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bu öğrenci numarasına sahip kullanıcı bulunamadı: " + studentNumber);
            }
            log.error("Feign error while resolving student number, status: {}", e.status());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kullanıcı servisi şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin.");
        } catch (Exception e) {
            log.error("Unexpected error while resolving student number", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Öğrenci bilgisi alınırken beklenmeyen bir hata oluştu.");
        }
    }
}
