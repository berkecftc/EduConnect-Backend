package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.request.ProfileChangeRequest;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubProfile;
import com.educonnect.clubservice.model.ClubProfileChange;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubProfileChangeRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ClubProfileService {

    private final ClubRepository clubRepository;
    private final ClubApprovalRequestRepository requestRepository;
    private final ClubProfileChangeRepository profileChangeRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubApprovalEngine approvalEngine;
    private final MinioService minioService;

    public ClubProfileService(ClubRepository clubRepository,
                              ClubApprovalRequestRepository requestRepository,
                              ClubProfileChangeRepository profileChangeRepository,
                              ClubAuthorizationService clubAuthorizationService,
                              ClubApprovalEngine approvalEngine,
                              MinioService minioService) {
        this.clubRepository = clubRepository;
        this.requestRepository = requestRepository;
        this.profileChangeRepository = profileChangeRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.approvalEngine = approvalEngine;
        this.minioService = minioService;
    }

    public ClubApprovalRequest requestProfileChange(UUID clubId, UUID userId, ProfileChangeRequest request) {
        Club club = openClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_PROFILE_CHANGE);
        requireNoPending(clubId, ApprovalType.CLUB_PROFILE_UPDATE, "PROFILE_CHANGE_PENDING",
                "Kulübün bekleyen bir bilgi değişikliği talebi var.");
        ClubProfile current = club.getProfile();
        String about = merge(request.about(), club.getAbout());
        ClubProfile proposed = new ClubProfile(
                request.category() != null ? request.category() : current.getCategory(),
                merge(request.contactEmail(), current.getContactEmail()),
                merge(request.websiteUrl(), current.getWebsiteUrl()),
                merge(request.instagramUrl(), current.getInstagramUrl()),
                merge(request.xUrl(), current.getXUrl()),
                merge(request.linkedinUrl(), current.getLinkedinUrl()));
        ClubApprovalRequest saved = requestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.CLUB_PROFILE_UPDATE,
                userId, null, null, null, request.note(), Instant.now()));
        profileChangeRepository.save(ClubProfileChange.ofProfile(saved.getId(), about, proposed));
        return approvalEngine.submit(club, saved);
    }

    public ClubApprovalRequest requestLogoChange(UUID clubId, UUID userId, MultipartFile file, String note) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_EMPTY", "Logo dosyası boş.");
        }
        Club club = openClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_LOGO_CHANGE);
        requireNoPending(clubId, ApprovalType.CLUB_LOGO_CHANGE, "LOGO_CHANGE_PENDING",
                "Kulübün bekleyen bir logo değişikliği talebi var.");
        String objectName = minioService.uploadFile(file, "logos", clubId + "-" + UUID.randomUUID());
        ClubApprovalRequest saved = requestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.CLUB_LOGO_CHANGE,
                userId, null, null, null, note, Instant.now()));
        profileChangeRepository.save(ClubProfileChange.ofLogo(saved.getId(), objectName));
        return approvalEngine.submit(club, saved);
    }

    private Club openClub(UUID clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulübün bilgileri değiştirilemez.");
        }
        return club;
    }

    private void requireNoPending(UUID clubId, ApprovalType type, String code, String message) {
        if (requestRepository.existsByClubIdAndTypeAndStatusIn(clubId, type, ApprovalStatus.PENDING)) {
            throw new ConflictException(code, message);
        }
    }

    private static String merge(String proposed, String current) {
        if (proposed == null) {
            return current;
        }
        String trimmed = proposed.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
