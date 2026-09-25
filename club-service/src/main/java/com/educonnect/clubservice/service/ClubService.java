package com.educonnect.clubservice.service;

import com.educonnect.clubservice.Repository.ClubCreationRequestRepository;
import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.client.UserLookup;
import com.educonnect.clubservice.config.ClubRabbitMQConfig; // RabbitMQ yapılandırmamız
import com.educonnect.clubservice.dto.message.ClubUpdateMessage;
import com.educonnect.clubservice.dto.request.*;
import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.clubservice.dto.response.ArchivedClubDTO;
import com.educonnect.clubservice.dto.response.ClubCatalogEntry;
import com.educonnect.clubservice.dto.response.ClubAdminSummaryDto;
import com.educonnect.clubservice.dto.response.ClubDetailsDTO;
import com.educonnect.clubservice.dto.response.ClubSummaryDTO;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.dto.response.MyClubMembershipDTO;
import com.educonnect.clubservice.dto.response.PageResponse;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.ArchivedClub;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.Repository.ArchivedClubRepository;
import com.educonnect.clubservice.Repository.ClubMembershipRepository;
import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional // Bu sınıftaki tüm metotlar veritabanı işlemi yapabilir
public class ClubService {

    private static final Logger log = LoggerFactory.getLogger(ClubService.class);

    // Gerekli bağımlılıklar
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final OutboxPublisher outboxPublisher;
    private final MinioService minioService;
    private final ClubCreationRequestRepository requestRepository; // Kulüp talepleri
    private final UserClient userClient;
    private final ArchivedClubRepository archivedClubRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubNotificationPublisher notificationPublisher;
    private final UserLookup userLookup;

    public ClubService(ClubRepository clubRepository,
                       ClubMembershipRepository membershipRepository,
                       OutboxPublisher outboxPublisher,
                       MinioService minioService,
                       ClubCreationRequestRepository requestRepository,
                       UserClient userClient,
                       ArchivedClubRepository archivedClubRepository,
                       ClubAuthorizationService clubAuthorizationService,
                       ClubCacheEvictor cacheEvictor,
                       ClubManagementStatusPublisher managementStatusPublisher,
                       ClubNotificationPublisher notificationPublisher,
                       UserLookup userLookup) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.outboxPublisher = outboxPublisher;
        this.minioService = minioService;
        this.requestRepository = requestRepository;
        this.userClient = userClient;
        this.archivedClubRepository = archivedClubRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.notificationPublisher = notificationPublisher;
        this.userLookup = userLookup;
    }

    /**
     * Admin tarafından yeni bir kulüp oluşturur.
     * (CreateClubRequest DTO'sunu kullanır)
     */
    public Club createClub(CreateClubRequest request) {

        // 1. Aynı isimde kulüp var mı diye kontrol et (opsiyonel ama önerilir)
        if (clubRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalStateException("Club with this name already exists.");
        }
        if (request.getClubPresidentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kulüp başkanı zorunludur.");
        }
        ensureValidAdvisor(request.getAcademicAdvisorId());
        ensureEligibleForManagement(request.getClubPresidentId());

        // 2. DTO'dan gelen bilgilerle yeni Club Entity'si oluştur
        Club newClub = new Club();
        newClub.setName(request.getName());
        newClub.setAbout(request.getAbout());
        newClub.setAcademicAdvisorId(request.getAcademicAdvisorId());

        // 3. Kulübü veritabanına kaydet
        Club savedClub = clubRepository.save(newClub);

        // 4. İstekte gelen 'clubPresidentId'yi bu kulübe BAŞKAN (President) olarak ata (ZORUNLU)
        if (request.getClubPresidentId() == null) {
            throw new IllegalArgumentException("clubPresidentId is required");
        }

        ClubMembership presidentMembership = new ClubMembership(
                savedClub.getId(),
                request.getClubPresidentId(),
                ClubPosition.PRESIDENT // Başkan rolü (enum'da bu isimde)
        );
        presidentMembership.setActive(true);
        presidentMembership.setTermStartDate(java.time.LocalDateTime.now());
        membershipRepository.save(presidentMembership);
        cacheEvictor.evictUser(request.getClubPresidentId());
        managementStatusPublisher.publishCurrentStatus(request.getClubPresidentId());

        return savedClub;
    }

    /**
     * Tüm kulüpleri özet olarak listeler.
     * (ClubSummaryDTO'yu kullanır)
     */
    @Transactional(readOnly = true) // Bu metot sadece okuma yapar
    public List<ClubSummaryDTO> getAllClubs() {
        return toClubSummaries(clubRepository.findAll());
    }

    @Transactional(readOnly = true)
    public PageResponse<ClubSummaryDTO> getClubsPage(int page, Integer size) {
        Page<Club> clubs = clubRepository.findAll(PageResponse.request(page, size, Sort.by("name").and(Sort.by("id"))));
        return PageResponse.of(clubs, toClubSummaries(clubs.getContent()));
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

    /**
     * Bir kulübün tüm detaylarını (üyeler dahil) getirir.
     * (ClubDetailsDTO ve MemberDTO'yu kullanır)
     */
    @Transactional(readOnly = true)
    public ClubDetailsDTO getClubDetails(UUID clubId, UUID viewerId) {
        // 1. Kulübü ID ile bul (bulamazsa hata fırlat)
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));

        // 2. Bu kulübün tüm üyeliklerini (ClubMembership Entity) veritabanından çek
        List<ClubMembership> memberships = membershipRepository.findByClubId(clubId);

        // 3. 'ClubMembership' listesini 'MemberDTO' listesine dönüştür
        boolean canViewAllMembers = clubAuthorizationService.accessOf(club, viewerId).has(ClubPermission.VIEW_MEMBERS);
        List<MemberDTO> memberDTOs = memberships.stream()
                .filter(ClubMembership::isActive)
                .filter(membership -> canViewAllMembers || membership.getClubRole().isManagement())
                .map(membership -> new MemberDTO(
                        membership.getStudentId(),
                        membership.getClubRole()
                ))
                .collect(Collectors.toList());

        // 4. Üye sayısını al
        long memberCount = membershipRepository.countByClubId(clubId);

        // 5. Danışman hoca bilgisini al
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

        // 6. Tüm bilgileri ana 'ClubDetailsDTO' içinde birleştir
        ClubDetailsDTO detailsDTO = new ClubDetailsDTO();
        detailsDTO.setId(club.getId());
        detailsDTO.setName(club.getName());
        detailsDTO.setAbout(club.getAbout());
        detailsDTO.setLogoUrl(club.getLogoUrl());
        detailsDTO.setAcademicAdvisorId(club.getAcademicAdvisorId());
        detailsDTO.setAdvisorName(advisorName);
        detailsDTO.setAdvisorTitle(advisorTitle);
        detailsDTO.setMemberCount(memberCount);
        detailsDTO.setMembers(memberDTOs); // Üye listesini DTO olarak ekle

        return detailsDTO;
    }

    @Transactional(readOnly = true)
    public List<UUID> getActiveMemberIds(UUID clubId) {
        return membershipRepository.findByClubId(clubId).stream()
                .filter(ClubMembership::isActive)
                .map(ClubMembership::getStudentId)
                .toList();
    }

    /**
     * Bir üyenin kulüpteki rolünü günceller (Kulüp Yetkilisi yapar).
     * (UpdateMemberRoleRequest DTO'sunu kullanır)
     *
     * @deprecated Bu metot artık kullanılmamalıdır. Görev değişiklikleri danışman onayına tabidir.
     *             Bunun yerine RoleChangeRequestService.createRoleChangeRequest() kullanın.
     *             Görevden alma için RoleChangeRequestService.revokeRole() kullanın.
     */
    @Deprecated
    public ClubMembership updateMemberRole(UUID clubId, UUID studentId, UpdateMemberRoleRequest request) {
        throw new UnsupportedOperationException(
                "Bu metot artık kullanılmamaktadır. Görev değişiklikleri danışman onayına tabidir. " +
                "Görev atamak için /api/clubs/{clubId}/role-change-requests endpoint'ini, " +
                "görevden almak için /api/clubs/{clubId}/members/{studentId}/role DELETE endpoint'ini kullanın."
        );
    }

    /**
     * Bir kulübü arşivleyerek kapatır (Soft Delete).
     * Admin kullanıcı tarafından yapılmalıdır.
     *
     * @param clubId Kapatılacak kulübün ID'si
     * @param reason Kapanış nedeni (opsiyonel)
     * @param adminId İşlemi yapan admin kullanıcının ID'si
     */
    @Transactional
    public void deleteClub(UUID clubId, String reason, UUID adminId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found with id: " + clubId));

        log.info("Archiving club: {} (ID: {}), reason: {}, by admin: {}",
            club.getName(), clubId, reason, adminId);

        // 1. Arşiv kaydı oluştur
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

        // 2. Arşive kaydet
        archivedClubRepository.save(archivedClub);
        log.info("Club archived successfully: {}", club.getName());

        // 3. Kulübün tüm üyeliklerini sil
        List<ClubMembership> members = membershipRepository.findByClubId(clubId);
        membershipRepository.deleteAll(members);
        membershipRepository.flush();
        members.forEach(member -> {
            cacheEvictor.evictUser(member.getStudentId());
            if (member.isActive() && member.getClubRole().isManagement()) {
                managementStatusPublisher.publishCurrentStatus(member.getStudentId());
            }
        });
        log.info("Deleted {} memberships for club: {}", members.size(), club.getName());

        // 4. Aktif tablodan kulübü sil
        clubRepository.delete(club);
        log.info("Club removed from active table: {}", club.getName());

        // 5. RabbitMQ ile event-service'e haber ver
        // Bu kulübün etkinliklerinin iptal edilmesi için
        try {
            ClubUpdateMessage message = new ClubUpdateMessage(
                clubId,
                "CLUB_DELETED",
                club.getName()
            );

            String routingKey = "club.deleted";
            outboxPublisher.publish(
                ClubRabbitMQConfig.CLUB_EXCHANGE_NAME,
                routingKey,
                message
            );

            log.info("Club deletion message sent to event-service for club: {}", clubId);
        } catch (Exception e) {
            log.error("Failed to send club deletion message: {}", e.getMessage(), e);
            // Mesaj gönderilemese bile kulüp arşivlendi, bu bir hata değil
        }
    }

    /**
     * Backward compatibility için eski metod imzası.
     * Yeni kod bu metodu kullanmamalı.
     *
     * @deprecated Use {@link #deleteClub(UUID, String, UUID)} instead
     */
    @Deprecated
    public void deleteClub(UUID clubId) {
        deleteClub(clubId, "Neden belirtilmedi", null);
    }

    /**
     * Kulüp bilgilerini günceller ve değişikliği RabbitMQ ile yayınlar.
     */
    public Club updateClub(UUID clubId, UpdateClubRequest request) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found"));

        // 1. Bilgileri Güncelle
        if (request.getName() != null) club.setName(request.getName());
        if (request.getAbout() != null) club.setAbout(request.getAbout());
        if (request.getAcademicAdvisorId() != null) club.setAcademicAdvisorId(request.getAcademicAdvisorId());

        Club updatedClub = clubRepository.save(club);

        // 2. RabbitMQ Mesajı Gönder (Sadece isim değiştiyse göndermek yeterli olabilir)
        if (request.getName() != null) { // İsim değiştiyse event-service bilmeli
            ClubUpdateMessage message = new ClubUpdateMessage(
                    updatedClub.getId(),
                    updatedClub.getName(),
                    updatedClub.getLogoUrl()
            );

            String routingKey = "club.updated"; // YENİ ROUTING KEY
            outboxPublisher.publish(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, routingKey, message);

            System.out.println("Club updated message sent: " + updatedClub.getName());
        }

        return updatedClub;
    }

    // --- YENİ METOT: ÖĞRENCİNİN KULÜPTEN AYRILMASI ---
    /**
     * Bir öğrencinin bir kulüpten ayrılmasını sağlar. Eğer öğrenci kulüp yetkilisi ise
     * ileride ek kurallar (örn: son yetkili ise engelle) eklenebilir.
     * @param clubId Ayrılmak istenen kulübün ID'si
     * @param studentId Ayrılmak isteyen öğrencinin ID'si
     */
    public void leaveClub(UUID clubId, UUID studentId) {
        // Kulüp var mı kontrolü
        if (!clubRepository.existsById(clubId)) {
            throw new RuntimeException("Club not found with id: " + clubId);
        }
        // Üyelik var mı kontrolü
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .orElseThrow(() -> new RuntimeException("Membership not found for this user and club"));

        if (membership.isActive() && membership.getClubRole() == ClubPosition.PRESIDENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Başkan, görevi danışman kararıyla sona ermeden kulüpten ayrılamaz.");
        }

        membershipRepository.delete(membership);
        membershipRepository.flush();
        cacheEvictor.evictUser(studentId);
        if (membership.isActive() && membership.getClubRole().isManagement()) {
            managementStatusPublisher.publishCurrentStatus(studentId);
        }
    }

    // --- YENİ METOT: KULÜP LOGOSU YÜKLEME ---
    /**
     * Bir kulübün logosunu MinIO'ya yükler ve veritabanını günceller.
     *
     * @param clubId        Güncellenecek kulübün ID'si
     * @param file          Logo dosyası (multipart)
     * @param requestingStudentId İsteği yapan öğrencinin ID'si (Token'dan alınır)
     * @return Yüklenen dosyanın MinIO'daki object name'i (örn: "logos/club-uuid.png")
     */
    public String updateClubLogo(UUID clubId, MultipartFile file, UUID requestingStudentId) {
        // 1. Kulübü bul
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"));

        // 2. GÜVENLİK KONTROLÜ: İsteği yapan, bu kulübün yetkilisi mi?
        // (Bu kontrolü Service katmanında yapmak daha güvenlidir)
        clubAuthorizationService.require(clubId, requestingStudentId, ClubPermission.UPDATE_CLUB_PROFILE);

        // 3. Dosyayı MinIO'ya yükle
        // (Dosya adını MinIO servisi belirlesin, örn: "logos/abc-123.png")
        String objectName = minioService.uploadFile(file, "logos", clubId.toString());

        // TODO: (İleride) 'club.getLogoUrl()' null değilse,
        // minioService.deleteFile(club.getLogoUrl()) çağrılmalı (eski logoyu silmek için).

        // 4. Kulübün veritabanındaki logo URL'sini güncelle
        club.setLogoUrl(objectName);
        clubRepository.save(club);

        // Not: Redis cache kullanıyorsak, burada @CacheEvict ile kulüp cache'ini temizlemeliyiz.

        return objectName;
    }

    @Transactional
    public String updateClubLogoByAdmin(UUID clubId, MultipartFile file) {
        System.out.println("DEBUG: Logo güncelleme başladı. ClubID: " + clubId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Kulüp bulunamadı"));

        System.out.println("DEBUG: Kulüp bulundu. Mevcut Logo URL: " + club.getLogoUrl());

        // Eski logoyu silme işlemini ŞİMDİLİK YAPMIYORUZ.
        // Çünkü eski URL bozuksa veya MinIO'da yoksa kod burada patlar ve işlem durur.
        // String oldLogoUrl = club.getLogoUrl();

        try {
            // 1. Yeni dosyayı yükle
            System.out.println("DEBUG: MinIO'ya yükleme başlıyor...");
            String newLogoUrl = minioService.uploadFile(file, "logos", clubId.toString());
            System.out.println("DEBUG: MinIO Yükleme Başarılı. Yeni URL: " + newLogoUrl);

            // 2. Yeni URL'i Set et
            club.setLogoUrl(newLogoUrl);

            // 3. Kaydet
            clubRepository.saveAndFlush(club); // save() yerine saveAndFlush() kullanıyoruz ki hatayı hemen görelim
            System.out.println("DEBUG: Veritabanı güncellendi.");

            return newLogoUrl;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("🔥🔥🔥 LOGO GÜNCELLEME HATASI 🔥🔥🔥", e);
            throw new RuntimeException("Logo güncellenemedi: " + e.getMessage());
        }
    }

    private void ensureValidAdvisor(UUID advisorId) {
        if (advisorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kulübün bir danışman akademisyeni olmalıdır.");
        }
        AcademicianSummary advisor;
        try {
            advisor = userClient.getAcademicianById(advisorId);
        } catch (Exception e) {
            log.warn("Advisor lookup failed for {}: {}", advisorId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danışman akademisyen bulunamadı.");
        }
        if (advisor == null || !"Academician".equalsIgnoreCase(advisor.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danışman olarak yalnızca bir akademisyen seçilebilir.");
        }
    }

    private void ensureEligibleForManagement(UUID studentId) {
        if (clubAuthorizationService.activeManagementPositionOf(studentId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu öğrencinin başka bir kulüpte yönetim görevi var. Bir öğrenci yalnızca bir kulüpte yönetim görevi alabilir.");
        }
    }

    // --- 1. ÖĞRENCİ: Talep Oluşturma ---
    public ClubCreationRequest submitClubCreationRequest(SubmitClubRequest request, UUID studentId) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kulüp adı zorunludur.");
        }
        ensureValidAdvisor(request.getAcademicAdvisorId());
        ensureEligibleForManagement(studentId);
        if (requestRepository.existsByRequestingStudentIdAndStatus(studentId, ClubCreationRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bekleyen bir kulüp kuruluş başvurunuz zaten var.");
        }
        if (clubRepository.findByName(request.getName()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu isimde bir kulüp zaten var.");
        }

        ClubCreationRequest newRequest = new ClubCreationRequest();
        newRequest.setClubName(request.getName());
        newRequest.setAbout(request.getAbout());
        newRequest.setSuggestedAdvisorId(request.getAcademicAdvisorId());
        newRequest.setRequestingStudentId(studentId); // Token'dan gelen ID

        ClubCreationRequest saved = requestRepository.save(newRequest);
        notificationPublisher.notifyUserAboutClubName(request.getAcademicAdvisorId(), request.getName(),
                "Kulüp kuruluş başvurusu",
                "\"" + request.getName() + "\" kulübü için danışmanlık onayınız bekleniyor.");
        return saved;
    }

    // --- 2. ADMIN: Talebi Onaylama ---
    public Club approveClubCreationRequest(UUID requestId) {
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı"));
        return approveCreationRequest(request, null);
    }

    public List<ClubCreationRequest> getPendingCreationRequestsForAdvisor(UUID advisorId) {
        return requestRepository.findByStatusAndSuggestedAdvisorId(ClubCreationRequestStatus.PENDING, advisorId);
    }

    public Club approveClubCreationRequestByAdvisor(UUID requestId, UUID advisorId) {
        ClubCreationRequest request = findCreationRequestForAdvisor(requestId, advisorId);
        return approveCreationRequest(request, advisorId);
    }

    public ClubCreationRequest rejectClubCreationRequestByAdvisor(UUID requestId, UUID advisorId, String reason) {
        ClubCreationRequest request = findCreationRequestForAdvisor(requestId, advisorId);
        request.setStatus(ClubCreationRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(advisorId);
        ClubCreationRequest saved = requestRepository.save(request);

        String message = "\"" + request.getClubName() + "\" kulübü için kuruluş başvurunuz danışman tarafından reddedildi.";
        if (reason != null && !reason.isBlank()) {
            message += " Neden: " + reason;
        }
        notificationPublisher.notifyUserAboutClubName(request.getRequestingStudentId(), request.getClubName(),
                "Kulüp kuruluş başvurusu", message);
        return saved;
    }

    private ClubCreationRequest findCreationRequestForAdvisor(UUID requestId, UUID advisorId) {
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Başvuru bulunamadı"));
        if (!advisorId.equals(request.getSuggestedAdvisorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu başvurunun önerilen danışmanı değilsiniz.");
        }
        return request;
    }

    private Club approveCreationRequest(ClubCreationRequest request, UUID approverId) {
        if (request.getStatus() != ClubCreationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu başvuru zaten işlenmiş.");
        }

        CreateClubRequest createDto = new CreateClubRequest();
        createDto.setName(request.getClubName());
        createDto.setAbout(request.getAbout());
        createDto.setAcademicAdvisorId(request.getSuggestedAdvisorId());
        createDto.setClubPresidentId(request.getRequestingStudentId()); // Talep eden kişi BAŞKAN olur

        Club newClub = createClub(createDto);

        request.setStatus(ClubCreationRequestStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(approverId);
        requestRepository.save(request);

        notificationPublisher.notifyUser(request.getRequestingStudentId(), newClub, "Kulüp kuruluş başvurusu",
                "\"" + newClub.getName() + "\" kulübünün kuruluşu onaylandı. Kulüp başkanı olarak atandınız.");
        return newClub;
    }

    // --- 3. ADMIN: Talepleri Listeleme ---
    public List<ClubCreationRequest> getPendingClubRequests() {
        return requestRepository.findByStatus(ClubCreationRequestStatus.PENDING);
    }

    /// İsteği reddetme metodu
    public void rejectClubCreationRequest(UUID requestId) {
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("İstek bulunamadı"));

        request.setStatus(ClubCreationRequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        requestRepository.save(request);
    }

    // 1. ADMIN İÇİN TÜM AKTİF KULÜPLERİ GETİR
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

    // 2. YÖNETİM KURULUNU GETİR (GÜNCELLENDİ)
    public List<MemberDTO> getClubBoardMembers(UUID clubId) {
        if (!clubRepository.existsById(clubId)) {
            throw new RuntimeException("Kulüp bulunamadı");
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

    // 3. BAŞKANI DEĞİŞTİR
    /**
     * @deprecated Bu metot artık kullanılmamalıdır. Başkan değişiklikleri danışman onayına tabidir.
     *             Önce mevcut başkanı RoleChangeRequestService.revokeRole() ile görevden alın,
     *             sonra RoleChangeRequestService.createRoleChangeRequest() ile yeni başkan talebi oluşturun.
     */
    @Deprecated
    @Transactional
    public void changeClubPresident(UUID clubId, UUID newPresidentId) {
        throw new UnsupportedOperationException(
                "Bu metot artık kullanılmamaktadır. Başkan değişiklikleri danışman onayına tabidir. " +
                "Önce mevcut başkanı görevden almak için /api/clubs/{clubId}/members/{studentId}/role DELETE endpoint'ini, " +
                "ardından yeni başkan atamak için /api/clubs/{clubId}/role-change-requests POST endpoint'ini kullanın."
        );
    }

    // 4. GEÇMİŞ BAŞKANLARI GÖRÜNTÜLE
    @Transactional(readOnly = true)
    public List<MemberDTO> getPastPresidents(UUID clubId) {
        // Kulübün var olup olmadığını kontrol et
        if (!clubRepository.existsById(clubId)) {
            throw new RuntimeException("Kulüp bulunamadı");
        }

        // Pasif olan ve ROLE_MEMBER'a dönüştürülmüş eski başkanları getir
        // changeClubPresident'te başkan ROLE_MEMBER yapılıyor ve isActive=false
        List<ClubMembership> pastPresidents = membershipRepository.findByClubId(clubId)
                .stream()
                .filter(m -> !m.isActive() && m.getTermEndDate() != null) // Pasif ve bitiş tarihi olan
                .sorted((a, b) -> b.getTermStartDate().compareTo(a.getTermStartDate())) // En yeniden eskiye
                .toList();

        Map<UUID, UserSummary> users = userLookup.usersById(pastPresidents.stream().map(ClubMembership::getStudentId).toList());

        return pastPresidents.stream()
                .map(m -> {
                    UserSummary user = users.get(m.getStudentId());
                    return new MemberDTO(
                            m.getStudentId(),
                            user != null ? user.getFirstName() : "Bilinmiyor",
                            user != null ? user.getLastName() : "User",
                            "Geçmiş Başkan", // Eski başkan olduğunu belirt
                            m.isActive(),
                            m.getTermStartDate(),
                            m.getTermEndDate()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Tüm arşivlenmiş kulüpleri listeler.
     * Sadece Admin kullanıcılar erişebilir.
     * @return Arşivlenmiş kulüplerin DTO listesi
     */
    @Transactional(readOnly = true)
    public List<ArchivedClubDTO> getAllArchivedClubs() {
        List<ArchivedClub> archivedClubs = archivedClubRepository.findAllByOrderByDeletedAtDesc();

        return archivedClubs.stream()
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

    // --- YENİ METOT: ÖĞRENCİNİN KULÜP ÜYELİKLERİNİ GETİR ---
    /**
     * Öğrencinin üye olduğu tüm kulüpleri getirir (aktif üyelikler)
     */
    @Cacheable(value = "studentClubMemberships", key = "#studentId")
    public List<MyClubMembershipDTO> getStudentClubMemberships(UUID studentId) {
        List<ClubMembership> memberships = membershipRepository.findByStudentId(studentId);
        Map<UUID, Club> clubs = clubsById(memberships);

        return memberships.stream().map(membership -> {
            Club club = clubs.get(membership.getClubId());
            if (club == null) return null;

            MyClubMembershipDTO dto = new MyClubMembershipDTO();
            dto.setClubId(club.getId());
            dto.setClubName(club.getName());
            dto.setLogoUrl(club.getLogoUrl());
            dto.setClubRole(membership.getClubRole());
            dto.setActive(membership.isActive());
            dto.setTermStartDate(membership.getTermStartDate());
            return dto;
        }).filter(dto -> dto != null).collect(Collectors.toList());
    }

    // ==================== CLUB OFFICIAL DASHBOARD METHODS ====================

    /**
     * Kulüp yetkilisinin yönetim kurulunda olduğu tüm kulüpleri getirir.
     * ROLE_CLUB_OFFICIAL, ROLE_VICE_PRESIDENT veya ROLE_BOARD_MEMBER rolüne sahip olduğu kulüpler.
     *
     * @param userId Kulüp yetkilisinin ID'si
     * @return Yönetici olduğu kulüplerin listesi
     */
    @Cacheable(value = "managedClubs", key = "#userId")
    public List<MyClubMembershipDTO> getManagedClubs(UUID userId) {
        List<ClubMembership> memberships = membershipRepository.findByStudentId(userId).stream()
                .filter(membership -> membership.getClubRole().isManagement())
                .filter(ClubMembership::isActive)
                .toList();
        Map<UUID, Club> clubs = clubsById(memberships);

        return memberships.stream()
                .map(membership -> {
                    Club club = clubs.get(membership.getClubId());
                    if (club == null) return null;

                    MyClubMembershipDTO dto = new MyClubMembershipDTO();
                    dto.setClubId(club.getId());
                    dto.setClubName(club.getName());
                    dto.setLogoUrl(club.getLogoUrl());
                    dto.setClubRole(membership.getClubRole());
                    dto.setActive(membership.isActive());
                    dto.setTermStartDate(membership.getTermStartDate());
                    return dto;
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    /**
     * Öğrencinin belirli bir kulübün aktif üyesi olup olmadığını kontrol eder.
     * Event-service gibi diğer servisler tarafından çağrılır.
     *
     * @param clubId Kulüp ID'si
     * @param studentId Öğrenci ID'si
     * @return true: Aktif üye, false: Üye değil veya pasif
     */
    public boolean isStudentMemberOfClub(UUID clubId, UUID studentId) {
        // Aktif üyelik kontrolü
        return membershipRepository.existsByClubIdAndStudentIdAndIsActive(clubId, studentId, true);
    }

    /**
     * Bir kulübün danışman akademisyen ID'sini döndürür.
     * Event-service tarafından etkinlik onayı için kullanılır.
     *
     * @param clubId Kulüp ID'si
     * @return Danışman akademisyen ID'si
     */
    @Transactional(readOnly = true)
    public UUID getClubAdvisorId(UUID clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı: " + clubId));
        return club.getAcademicAdvisorId();
    }

    /**
     * Bir danışman akademisyenin sorumlu olduğu kulüplerin ID listesini döndürür.
     * Event-service tarafından bekleyen etkinlikleri listelemek için kullanılır.
     *
     * @param advisorId Danışman akademisyen ID'si
     * @return Kulüp ID listesi
     */
    @Transactional(readOnly = true)
    public List<UUID> getClubIdsByAdvisorId(UUID advisorId) {
        List<Club> clubs = clubRepository.findByAcademicAdvisorId(advisorId);
        return clubs.stream()
                .map(Club::getId)
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

    @Transactional(readOnly = true)
    public List<ClubCatalogEntry> getClubCatalog() {
        return clubRepository.findAll(Sort.by("name")).stream()
                .map(club -> new ClubCatalogEntry(club.getId(), club.getName(), club.getAbout()))
                .toList();
    }
}
