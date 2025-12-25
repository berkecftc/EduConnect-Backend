package com.educonnect.clubservice.service;

import com.educonnect.clubservice.Repository.ClubCreationRequestRepository;
import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.config.ClubRabbitMQConfig; // RabbitMQ yapılandırmamız
import com.educonnect.clubservice.dto.message.AssignClubRoleMessage;
import com.educonnect.clubservice.dto.message.ClubUpdateMessage;
import com.educonnect.clubservice.dto.message.RevokeClubRoleMessage;
import com.educonnect.clubservice.dto.request.*;
import com.educonnect.clubservice.dto.response.ArchivedClubDTO;
import com.educonnect.clubservice.dto.response.ClubAdminSummaryDto;
import com.educonnect.clubservice.dto.response.ClubDetailsDTO;
import com.educonnect.clubservice.dto.response.ClubSummaryDTO;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.ArchivedClub;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubRole;
import com.educonnect.clubservice.Repository.ArchivedClubRepository;
import com.educonnect.clubservice.Repository.ClubMembershipRepository;
import com.educonnect.clubservice.Repository.ClubRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@Transactional // Bu sınıftaki tüm metotlar veritabanı işlemi yapabilir
public class ClubService {

    private static final Logger log = LoggerFactory.getLogger(ClubService.class);

    // Gerekli bağımlılıklar
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final RabbitTemplate rabbitTemplate; // RabbitMQ ile konuşmak için
    private final MinioService minioService;
    private final ClubCreationRequestRepository requestRepository; // Kulüp talepleri
    private final UserClient userClient;
    private final ArchivedClubRepository archivedClubRepository;

    public ClubService(ClubRepository clubRepository,
                       ClubMembershipRepository membershipRepository,
                       RabbitTemplate rabbitTemplate,
                       MinioService minioService,
                       ClubCreationRequestRepository requestRepository,
                       UserClient userClient,
                       ArchivedClubRepository archivedClubRepository) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.minioService = minioService;
        this.requestRepository = requestRepository;
        this.userClient = userClient;
        this.archivedClubRepository = archivedClubRepository;
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
                ClubRole.ROLE_CLUB_OFFICIAL // Başkan rolü (enum'da bu isimde)
        );
        presidentMembership.setActive(true);
        presidentMembership.setTermStartDate(java.time.LocalDateTime.now());
        membershipRepository.save(presidentMembership);

        // 5. RabbitMQ ile auth-service'e mesaj gönder: Başkana ROLE_CLUB_OFFICIAL rolü ata
        try {
            AssignClubRoleMessage message = new AssignClubRoleMessage(
                    request.getClubPresidentId(),
                    "ROLE_CLUB_OFFICIAL",
                    savedClub.getId()
            );

            String routingKey = "user.role.assign";
            rabbitTemplate.convertAndSend(
                    ClubRabbitMQConfig.EXCHANGE_NAME,
                    routingKey,
                    message
            );

            log.info("Sent role assignment message for user {} to become ROLE_CLUB_OFFICIAL of club {}",
                    request.getClubPresidentId(), savedClub.getId());
        } catch (Exception e) {
            log.error("Failed to send role assignment message: {}", e.getMessage(), e);
            // İsterse burada exception fırlatabilirsiniz veya sadece log bırakabilirsiniz
            // Şu an için sadece log bırakıyoruz, kulüp oluşumu başarılı olsun
        }

        return savedClub;
    }

    /**
     * Tüm kulüpleri özet olarak listeler.
     * (ClubSummaryDTO'yu kullanır)
     */
    @Transactional(readOnly = true) // Bu metot sadece okuma yapar
    public List<ClubSummaryDTO> getAllClubs() {
        // 1. Tüm kulüp Entity'lerini veritabanından çek
        List<Club> clubs = clubRepository.findAll();

        // 2. Entity listesini DTO listesine dönüştür
        return clubs.stream()
                .map(club -> new ClubSummaryDTO(
                        club.getId(),
                        club.getName(),
                        club.getLogoUrl()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Bir kulübün tüm detaylarını (üyeler dahil) getirir.
     * (ClubDetailsDTO ve MemberDTO'yu kullanır)
     */
    @Transactional(readOnly = true)
    public ClubDetailsDTO getClubDetails(UUID clubId) {
        // 1. Kulübü ID ile bul (bulamazsa hata fırlat)
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found with id: " + clubId));

        // 2. Bu kulübün tüm üyeliklerini (ClubMembership Entity) veritabanından çek
        List<ClubMembership> memberships = membershipRepository.findByClubId(clubId);

        // 3. 'ClubMembership' listesini 'MemberDTO' listesine dönüştür
        List<MemberDTO> memberDTOs = memberships.stream()
                .map(membership -> new MemberDTO(
                        membership.getStudentId(),
                        membership.getClubRole()
                ))
                .collect(Collectors.toList());

        // TODO: (Gelecek Geliştirmesi) memberDTOs listesindeki studentId'leri kullanarak
        // user-service'e bir API isteği atıp, üye adlarını ve resimlerini de bu DTO'ya ekle.

        // 4. Tüm bilgileri ana 'ClubDetailsDTO' içinde birleştir
        ClubDetailsDTO detailsDTO = new ClubDetailsDTO();
        detailsDTO.setId(club.getId());
        detailsDTO.setName(club.getName());
        detailsDTO.setAbout(club.getAbout());
        detailsDTO.setLogoUrl(club.getLogoUrl());
        detailsDTO.setAcademicAdvisorId(club.getAcademicAdvisorId());
        detailsDTO.setMembers(memberDTOs); // Üye listesini DTO olarak ekle

        return detailsDTO;
    }

    /**
     * Bir kulübe yeni bir üye ekler (Kulüp Yetkilisi yapar).
     * (AddMemberRequest DTO'sunu kullanır)
     */
    public ClubMembership addMemberToClub(UUID clubId, AddMemberRequest request) {

        // 1. Zaten üye mi diye kontrol et
        if (membershipRepository.findByClubIdAndStudentId(clubId, request.getStudentId()).isPresent()) {
            throw new IllegalStateException("This student is already a member.");
        }

        // 2. Yeni üyeliği oluştur
        ClubMembership newMembership = new ClubMembership(
                clubId,
                request.getStudentId(),
                request.getClubRole() // DTO'dan gelen rol (örn: ROLE_BOARD_MEMBER)
        );
        return membershipRepository.save(newMembership);
    }

    /**
     * Bir üyenin kulüpteki rolünü günceller (Kulüp Yetkilisi yapar).
     * (UpdateMemberRoleRequest DTO'sunu kullanır)
     */
    public ClubMembership updateMemberRole(UUID clubId, UUID studentId, UpdateMemberRoleRequest request) {

        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .orElseThrow(() -> new RuntimeException("Membership not found for this user and club"));

        membership.setClubRole(request.getNewClubRole());

        return membershipRepository.save(membership);
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
            rabbitTemplate.convertAndSend(
                ClubRabbitMQConfig.EXCHANGE_NAME,
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
            rabbitTemplate.convertAndSend(ClubRabbitMQConfig.EXCHANGE_NAME, routingKey, message);

            System.out.println("Club updated message sent: " + updatedClub.getName());
        }

        return updatedClub;
    }

    // --- YENİ METOT: ÖĞRENCİNİN KULÜBE KATILMASI ---
    /**
     * Bir öğrencinin bir kulübe 'ROLE_MEMBER' (Normal Üye) olarak katılmasını sağlar.
     * @param clubId Katılmak istenen kulübün ID'si
     * @param studentId Katılmak isteyen öğrencinin ID'si (Token'dan alınacak)
     */
    public void joinClub(UUID clubId, UUID studentId) {

        // 1. Kulübün var olup olmadığını kontrol et
        if (!clubRepository.existsById(clubId)) {
            throw new RuntimeException("Club not found with id: " + clubId);
            // (Daha iyisi: Kendi 'ResourceNotFoundException' sınıfınızı kullanın)
        }

        // 2. Öğrencinin bu kulübe zaten üye olup olmadığını kontrol et
        if (membershipRepository.findByClubIdAndStudentId(clubId, studentId).isPresent()) {
            throw new IllegalStateException("Student is already a member of this club.");
            // (Daha iyisi: 409 Conflict hatası döndürün)
        }

        // 3. Yeni üyelik kaydını oluştur
        // DİKKAT: Rol, DTO'dan değil, doğrudan 'ROLE_MEMBER' olarak atanır
        ClubMembership newMembership = new ClubMembership(
                clubId,
                studentId,
                ClubRole.ROLE_MEMBER // Katılan kişi her zaman 'Normal Üye' olarak başlar
        );

        // 4. Yeni üyeliği veritabanına kaydet
        membershipRepository.save(newMembership);

        // Opsiyonel: Kulüp yetkilisine (Başkan/YK) yeni bir üye katıldığına
        // dair bir bildirim (RabbitMQ mesajı) gönderilebilir.
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

        // TODO: Eğer membership.getClubRole() == ROLE_CLUB_OFFICIAL ise ve kulüpte başka resmi yetkili yoksa ayrılmasını engelle.

        membershipRepository.delete(membership);
    }

    // --- YENİ METOT: KULÜP LOGOSU YÜKLEME ---
    /**
     * Bir kulübün logosunu MinIO'ya yükler ve veritabanını günceller.
     * Sadece Admin veya o kulübün YK üyesi/Başkanı yapabilir.
     *
     * @param clubId        Güncellenecek kulübün ID'si
     * @param file          Logo dosyası (multipart)
     * @param requestingStudentId İsteği yapan öğrencinin ID'si (Token'dan alınır)
     * @return Yüklenen dosyanın MinIO'daki object name'i (örn: "logos/club-uuid.png")
     */
    public String updateClubLogo(UUID clubId, MultipartFile file, UUID requestingStudentId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            log.debug("updateClubLogo invoked by principal={}, authorities={}", auth.getPrincipal(), auth.getAuthorities());
        } else {
            log.debug("updateClubLogo invoked with no authentication in context");
        }

        // 1. Kulübü bul
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"));

        // 2. GÜVENLİK KONTROLÜ: İsteği yapan, bu kulübün yetkilisi mi?
        // (Bu kontrolü Service katmanında yapmak daha güvenlidir)
        checkClubOfficialAuthorization(clubId, requestingStudentId);

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

        } catch (Exception e) {
            log.error("🔥🔥🔥 LOGO GÜNCELLEME HATASI 🔥🔥🔥", e);
            throw new RuntimeException("Logo güncellenemedi: " + e.getMessage());
        }
    }

    // --- YENİ YARDIMCI METOT (Güvenlik için) ---
    private void checkClubOfficialAuthorization(UUID clubId, UUID studentId) {
        // Önce SecurityContext'ten ADMIN rolü var mı bak. Varsa direkt izin ver.
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            boolean isAdmin = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_ADMIN"));
            if (isAdmin) {
                return; // Admin her kulüp üzerinde işlem yapabilir
            }
        }

        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this club"));

        // Eğer üye ama rolü Başkan, Bşk. Yrd. veya YK Üyesi DEĞİLSE, reddet
        if (membership.getClubRole() != ClubRole.ROLE_CLUB_OFFICIAL&&
                membership.getClubRole() != ClubRole.ROLE_VICE_PRESIDENT &&
                membership.getClubRole() != ClubRole.ROLE_BOARD_MEMBER) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to manage this club's logo");
        }
    }


    // --- 1. ÖĞRENCİ: Talep Oluşturma ---
    public void submitClubCreationRequest(SubmitClubRequest request, UUID studentId) {
        // Aynı isimde kulüp var mı veya bekleyen talep var mı kontrol et (Opsiyonel)

        ClubCreationRequest newRequest = new ClubCreationRequest();
        newRequest.setClubName(request.getName());
        newRequest.setAbout(request.getAbout());
        newRequest.setSuggestedAdvisorId(request.getAcademicAdvisorId());
        newRequest.setRequestingStudentId(studentId); // Token'dan gelen ID

        requestRepository.save(newRequest);
    }

    // --- 2. ADMIN: Talebi Onaylama ---
    public Club approveClubCreationRequest(UUID requestId) {
        // Talebi bul
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is already processed.");
        }

        // Mevcut 'createClub' mantığını kullanarak gerçek kulübü oluştur
        // Bunun için CreateClubRequest DTO'sunu manuel dolduruyoruz
        CreateClubRequest createDto = new CreateClubRequest();
        createDto.setName(request.getClubName());
        createDto.setAbout(request.getAbout());
        createDto.setAcademicAdvisorId(request.getSuggestedAdvisorId());
        createDto.setClubPresidentId(request.getRequestingStudentId()); // Talep eden kişi BAŞKAN olur

        // Mevcut metodu çağır (Bu metot kulübü kurar, başkanı atar ve RabbitMQ mesajını atar)
        Club newClub = createClub(createDto);

        // Talebin durumunu güncelle
        request.setStatus("APPROVED");
        requestRepository.save(request);

        return newClub;
    }

    // --- 3. ADMIN: Talepleri Listeleme ---
    public List<ClubCreationRequest> getPendingClubRequests() {
        return requestRepository.findByStatus("PENDING");
    }

    /// İsteği reddetme metodu
    public void rejectClubCreationRequest(UUID requestId) {
        ClubCreationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("İstek bulunamadı"));

        // YÖNTEM 1: Durumu REJECTED yapıp saklamak (Tavsiye edilen)
        // Eğer Status enum'ında REJECTED yoksa eklemen gerekir.
        // request.setStatus(RequestStatus.REJECTED);
        // requestRepository.save(request);

        // YÖNTEM 2: Direkt Silmek (Daha basit)
        requestRepository.delete(request);
    }

    // 1. ADMIN İÇİN TÜM AKTİF KULÜPLERİ GETİR
    public List<ClubAdminSummaryDto> getAllClubsForAdmin() {
        List<Club> clubs = clubRepository.findAll();

        return clubs.stream().map(club -> {
            // Başkanı Bul (Rolü CLUB_OFFICIAL olan)
            List<ClubMembership> memberships = membershipRepository.findByClubId(club.getId());

            UUID presidentId = memberships.stream()
                    .filter(m -> m.getClubRole() == ClubRole.ROLE_CLUB_OFFICIAL)
                    .findFirst()
                    .map(ClubMembership::getStudentId)
                    .orElse(null);

            // TODO: presidentId ile user-service'e istek atarak ismi çek
            String presidentName = presidentId != null ? presidentId.toString() : "Atanmamış";

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

        List<ClubMembership> memberships = membershipRepository.findByClubId(clubId);

        return memberships.stream()
                // 👇 FİLTRE BURADA GÜNCELLENDİ:
                // Sadece Başkan ve Yetkilileri değil, "BOARD" (Yönetim Kurulu) üyelerini de dahil et.
                .filter(m -> {
                    String r = m.getClubRole().toString();
                    return r.contains("OFFICIAL") ||
                            r.contains("PRESIDENT") ||
                            r.contains("BOARD") ||   // <-- EKLENDİ (Yönetim Kurulu)
                            r.contains("ADMIN");     // <-- EKLENDİ (Varsa adminler)
                })
                .map(m -> {
                    // 1. User Service'ten ismi çek
                    String fName = "Bilinmiyor";
                    String lName = "User";
                    try {
                        UserSummary user = userClient.getUserById(m.getStudentId());
                        if (user != null) {
                            fName = user.getFirstName();
                            lName = user.getLastName();
                        }
                    } catch (Exception e) {
                        System.err.println("User Service hatası: " + e.getMessage());
                    }

                    // 2. DTO oluştur
                    return new MemberDTO(
                            m.getStudentId(),
                            fName,
                            lName,
                            m.getClubRole().toString() // Rolü string olarak gönderiyoruz
                    );
                })
                .collect(Collectors.toList());
    }

    // 3. BAŞKANI DEĞİŞTİR
    @Transactional
    public void changeClubPresident(UUID clubId, UUID newPresidentId) {
        // Kulübün var olup olmadığını kontrol et
        if (!clubRepository.existsById(clubId)) {
            throw new RuntimeException("Kulüp bulunamadı");
        }

        List<ClubMembership> memberships = membershipRepository.findByClubId(clubId);

        // Eski aktif başkanları bul ve pasife çek
        List<ClubMembership> oldPresidents = memberships.stream()
                .filter(m -> m.getClubRole() == ClubRole.ROLE_CLUB_OFFICIAL && m.isActive())
                .toList();

        for (ClubMembership oldPresident : oldPresidents) {
            log.info("Processing old president: studentId={}, clubRole={}, isActive={}",
                    oldPresident.getStudentId(), oldPresident.getClubRole(), oldPresident.isActive());

            // Eski başkanı pasife çek
            oldPresident.setActive(false);
            oldPresident.setTermEndDate(java.time.LocalDateTime.now());
            oldPresident.setClubRole(ClubRole.ROLE_MEMBER); // Rolü üye yap
            membershipRepository.save(oldPresident);

            log.info("Updated old president to ROLE_MEMBER: studentId={}", oldPresident.getStudentId());

            // RabbitMQ ile auth-service'e rol kaldırma mesajı gönder
            try {
                RevokeClubRoleMessage revokeMessage = new RevokeClubRoleMessage(
                        oldPresident.getStudentId(),
                        "ROLE_CLUB_OFFICIAL",
                        clubId
                );

                log.info("Sending role revoke message to RabbitMQ: userId={}, role={}, clubId={}",
                        oldPresident.getStudentId(), "ROLE_CLUB_OFFICIAL", clubId);

                rabbitTemplate.convertAndSend(
                        ClubRabbitMQConfig.EXCHANGE_NAME,
                        "user.role.revoke",
                        revokeMessage
                );

                log.info("Successfully sent role revoke message for user {} from club {}",
                        oldPresident.getStudentId(), clubId);
            } catch (Exception e) {
                log.error("Failed to send role revoke message for user {}: {}",
                        oldPresident.getStudentId(), e.getMessage(), e);
            }
        }

        // Yeni başkanı bul veya oluştur
        ClubMembership newPrez = memberships.stream()
                .filter(m -> m.getStudentId().equals(newPresidentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Bu öğrenci kulübe üye değil!"));

        // Yeni başkanı aktif yap
        newPrez.setClubRole(ClubRole.ROLE_CLUB_OFFICIAL);
        newPrez.setActive(true);
        newPrez.setTermStartDate(java.time.LocalDateTime.now());
        newPrez.setTermEndDate(null); // Henüz bitiş tarihi yok
        membershipRepository.save(newPrez);

        // RabbitMQ ile auth-service'e rol atama mesajı gönder
        try {
            AssignClubRoleMessage assignMessage = new AssignClubRoleMessage(
                    newPresidentId,
                    "ROLE_CLUB_OFFICIAL",
                    clubId
            );

            rabbitTemplate.convertAndSend(
                    ClubRabbitMQConfig.EXCHANGE_NAME,
                    "user.role.assign",
                    assignMessage
            );

            log.info("Sent role assignment message for user {} to become ROLE_CLUB_OFFICIAL of club {}",
                    newPresidentId, clubId);
        } catch (Exception e) {
            log.error("Failed to send role assignment message: {}", e.getMessage(), e);
        }
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

        // DTO'ya dönüştür
        return pastPresidents.stream()
                .map(m -> {
                    // User Service'ten ismi çek
                    String fName = "Bilinmiyor";
                    String lName = "User";
                    try {
                        UserSummary user = userClient.getUserById(m.getStudentId());
                        if (user != null) {
                            fName = user.getFirstName();
                            lName = user.getLastName();
                        }
                    } catch (Exception e) {
                        log.error("User Service hatası: {}", e.getMessage());
                    }

                    // DTO oluştur (tarih bilgisiyle birlikte)
                    return new MemberDTO(
                            m.getStudentId(),
                            fName,
                            lName,
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
}
