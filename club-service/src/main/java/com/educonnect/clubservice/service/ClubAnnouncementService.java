package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.response.AnnouncementResponse;
import com.educonnect.clubservice.dto.response.PageResponse;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubAnnouncement;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.repository.ClubAnnouncementRepository;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAccess;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ClubAnnouncementService {

    private final ClubRepository clubRepository;
    private final ClubApprovalRequestRepository requestRepository;
    private final ClubAnnouncementRepository announcementRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubApprovalEngine approvalEngine;
    private final ClubDecisionLog decisionLog;

    public ClubAnnouncementService(ClubRepository clubRepository,
                                   ClubApprovalRequestRepository requestRepository,
                                   ClubAnnouncementRepository announcementRepository,
                                   ClubAuthorizationService clubAuthorizationService,
                                   ClubApprovalEngine approvalEngine,
                                   ClubDecisionLog decisionLog) {
        this.clubRepository = clubRepository;
        this.requestRepository = requestRepository;
        this.announcementRepository = announcementRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.approvalEngine = approvalEngine;
        this.decisionLog = decisionLog;
    }

    public ClubApprovalRequest submit(UUID clubId, UUID userId, String title, String body) {
        Club club = findClub(clubId);
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulüp duyuru yayımlayamaz.");
        }
        clubAuthorizationService.require(clubId, userId, ClubPermission.PREPARE_ANNOUNCEMENT);
        Instant now = Instant.now();
        ClubApprovalRequest request = requestRepository.save(new ClubApprovalRequest(clubId, ApprovalType.CLUB_ANNOUNCEMENT,
                userId, null, null, null, null, now));
        announcementRepository.save(new ClubAnnouncement(clubId, request.getId(), title.strip(), body.strip(), userId, now));
        return approvalEngine.submit(club, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> announcementsOf(UUID clubId, UUID viewerId, int page, Integer size) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, viewerId, ClubPermission.VIEW_MEMBERS);
        Page<ClubAnnouncement> announcements = announcementRepository.findByClubIdAndPublishedAtIsNotNullAndRemovedAtIsNull(
                clubId, PageResponse.request(page, size, Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by("id"))));
        return PageResponse.of(announcements, announcements.getContent().stream().map(AnnouncementResponse::of).toList());
    }

    public AnnouncementResponse remove(UUID clubId, UUID announcementId, UUID userId) {
        ClubAccess access = clubAuthorizationService.accessOf(clubId, userId);
        if (!access.has(ClubPermission.APPROVE_AS_PRESIDENT) && !access.has(ClubPermission.ADVISE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Duyuruyu yalnızca başkan veya danışman kaldırabilir.");
        }
        ClubAnnouncement announcement = announcementRepository.findById(announcementId)
                .filter(found -> found.getClubId().equals(clubId))
                .filter(ClubAnnouncement::isVisible)
                .orElseThrow(() -> new NotFoundException("ANNOUNCEMENT_NOT_FOUND", "Duyuru bulunamadı"));
        announcement.remove(userId, Instant.now());
        announcementRepository.save(announcement);
        decisionLog.record(clubId, DecisionAction.ANNOUNCEMENT_REMOVED, userId, null, announcement.getTitle());
        return AnnouncementResponse.of(announcement);
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }
}
