package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ApprovalChainSettings;
import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.ClubUpdateMessage;
import com.educonnect.clubservice.dto.request.UpdateClubRequest;
import com.educonnect.clubservice.model.ArchivedClub;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.model.MembershipEndReason;
import com.educonnect.clubservice.repository.ArchivedClubRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.LogValues;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.educonnect.common.messaging.notification.NotificationCategory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Service
@Transactional
public class ClubLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ClubLifecycleService.class);

    private static final String ROUTING_KEY_CLUB_UPDATED = "club.updated";
    private static final String ROUTING_KEY_CLUB_DELETED = "club.deleted";

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ArchivedClubRepository archivedClubRepository;
    private final OutboxPublisher outboxPublisher;
    private final MinioService minioService;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubDecisionLog decisionLog;
    private final ApprovalChainSettings approvalChainSettings;
    private final AdvisorDirectory advisorDirectory;
    private final ClubNotificationPublisher notificationPublisher;
    private final ClubLeadershipService leadershipService;
    private final RoleChangeUserNames userNames;

    public ClubLifecycleService(ClubRepository clubRepository,
                                ClubMembershipRepository membershipRepository,
                                ArchivedClubRepository archivedClubRepository,
                                OutboxPublisher outboxPublisher,
                                MinioService minioService,
                                ClubAuthorizationService clubAuthorizationService,
                                ClubCacheEvictor cacheEvictor,
                                ClubManagementStatusPublisher managementStatusPublisher,
                                ClubDecisionLog decisionLog,
                                ApprovalChainSettings approvalChainSettings,
                                AdvisorDirectory advisorDirectory,
                                ClubNotificationPublisher notificationPublisher,
                                ClubLeadershipService leadershipService,
                                RoleChangeUserNames userNames) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.archivedClubRepository = archivedClubRepository;
        this.outboxPublisher = outboxPublisher;
        this.minioService = minioService;
        this.clubAuthorizationService = clubAuthorizationService;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.decisionLog = decisionLog;
        this.approvalChainSettings = approvalChainSettings;
        this.advisorDirectory = advisorDirectory;
        this.notificationPublisher = notificationPublisher;
        this.leadershipService = leadershipService;
        this.userNames = userNames;
    }

    public Club updateClub(UUID clubId, UpdateClubRequest request) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));

        if (request.getName() != null) club.setName(request.getName());
        if (request.getAbout() != null) club.setAbout(request.getAbout());
        UUID previousAdvisorId = club.getAcademicAdvisorId();
        boolean advisorChanged = request.getAcademicAdvisorId() != null
                && !request.getAcademicAdvisorId().equals(previousAdvisorId);
        if (advisorChanged) {
            advisorDirectory.requireAcademician(request.getAcademicAdvisorId());
            club.setAcademicAdvisorId(request.getAcademicAdvisorId());
        }

        Club updatedClub = clubRepository.save(club);
        if (advisorChanged) {
            notificationPublisher.notifyAdvisor(updatedClub, "Kulüp danışmanlığı",
                    "\"" + updatedClub.getName() + "\" kulübüne akademik danışman olarak atandınız.");
            notificationPublisher.notifyUser(previousAdvisorId, updatedClub, "Kulüp danışmanlığı",
                    "\"" + updatedClub.getName() + "\" kulübündeki danışmanlığınız sona erdi.");
        }

        if (request.getName() != null) {
            ClubUpdateMessage message = new ClubUpdateMessage(
                    updatedClub.getId(),
                    updatedClub.getName(),
                    updatedClub.getLogoUrl()
            );
            outboxPublisher.publish(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CLUB_UPDATED, message);

            log.info("Club updated message sent: {}", LogValues.safe(updatedClub.getName()));
        }

        return updatedClub;
    }

    public String updateClubLogo(UUID clubId, MultipartFile file, UUID requestingStudentId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"));

        clubAuthorizationService.require(clubId, requestingStudentId, ClubPermission.UPDATE_CLUB_PROFILE);
        if (approvalChainSettings.enabled()) {
            throw new ConflictException("APPROVAL_REQUIRED", "Logo değişikliği için onay talebi açılmalı.");
        }

        String objectName = minioService.uploadFile(file, "logos", clubId.toString());

        club.setLogoUrl(objectName);
        clubRepository.save(club);

        return objectName;
    }

    @Transactional
    public String updateClubLogoByAdmin(UUID clubId, MultipartFile file) {
        log.debug("Logo güncelleme başladı. clubId={}", clubId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));

        log.debug("Kulüp bulundu. Mevcut logo: {}", club.getLogoUrl());

        log.debug("Logo MinIO'ya yükleniyor");
        String newLogoUrl = minioService.uploadFile(file, "logos", clubId.toString());
        log.debug("Logo yüklendi: {}", newLogoUrl);

        club.setLogoUrl(newLogoUrl);
        clubRepository.saveAndFlush(club);
        log.debug("Kulüp logosu veritabanında güncellendi");

        return newLogoUrl;
    }

    @Transactional
    public void deleteClub(UUID clubId, String reason, UUID adminId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found with id: " + clubId));

        log.info("Archiving club: {} (ID: {}), reason: {}, by admin: {}",
            club.getName(), clubId, reason, adminId);

        ArchivedClub archivedClub = new ArchivedClub(
            club.getId(),
            club.getName(),
            club.getAbout(),
            club.getLogoUrl(),
            club.getAcademicAdvisorId(),
            LocalDateTime.now(),
            reason != null ? reason : "Admin tarafından kapatıldı",
            adminId
        );
        archivedClubRepository.save(archivedClub);
        log.info("Club archived successfully: {}", club.getName());

        List<ClubMembership> members = membershipRepository.findByClubId(clubId);
        List<UUID> recipients = new ArrayList<>(members.stream()
                .filter(ClubMembership::isActive)
                .map(ClubMembership::getStudentId)
                .toList());
        recipients.add(club.getAcademicAdvisorId());
        membershipRepository.deleteAll(members);
        membershipRepository.flush();
        members.forEach(member -> {
            cacheEvictor.evictUser(member.getStudentId());
            if (member.isActive() && member.getClubRole().isManagement()) {
                managementStatusPublisher.publishCurrentStatus(member.getStudentId());
            }
        });
        log.info("Deleted {} memberships for club: {}", members.size(), club.getName());

        clubRepository.delete(club);
        log.info("Club removed from active table: {}", club.getName());
        notificationPublisher.publish(recipients, null, NotificationCategory.CLUB_MANAGEMENT,
                ClubNotificationPublisher.TYPE_NOTICE, club.getName() + ": Kulüp kapatıldı",
                "\"" + club.getName() + "\" kulübü yönetim kararıyla kapatıldı; kulübün gelecekteki etkinlikleri iptal edildi."
                        + (reason != null && !reason.isBlank() ? " Neden: " + reason : ""));

        try {
            ClubUpdateMessage message = new ClubUpdateMessage(clubId, club.getName(), null);
            outboxPublisher.publish(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CLUB_DELETED, message);

            log.info("Club deletion message sent to event-service for club: {}", clubId);
        } catch (Exception e) {
            log.error("Failed to send club deletion message: {}", e.getMessage(), e);
        }
    }

    public void leaveClub(UUID clubId, UUID studentId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found with id: " + clubId));
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulübün üyelikleri değiştirilemez.");
        }
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new NotFoundException("MEMBERSHIP_NOT_FOUND", "Membership not found for this user and club"));

        if (membership.getClubRole() == ClubPosition.PRESIDENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Başkan, görevi danışman kararıyla sona ermeden kulüpten ayrılamaz.");
        }

        boolean wasManagement = membership.getClubRole().isManagement();
        membership.end(MembershipEndReason.LEFT, LocalDateTime.now());
        membershipRepository.save(membership);
        decisionLog.record(clubId, DecisionAction.MEMBER_LEFT, studentId, studentId, null);
        cacheEvictor.evictUser(studentId);
        if (wasManagement) {
            managementStatusPublisher.publishCurrentStatus(studentId);
        }
        leadershipService.currentLeaderOf(clubId)
                .ifPresent(leaderId -> notificationPublisher.notifyUser(leaderId, club, "Üye ayrıldı",
                        userNames.nameOf(studentId) + " kulüpten ayrıldı."));
    }
}
