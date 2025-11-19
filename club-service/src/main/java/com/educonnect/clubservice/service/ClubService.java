package com.educonnect.clubservice.service;

import com.educonnect.clubservice.Repository.ClubCreationRequestRepository;
import com.educonnect.clubservice.config.ClubRabbitMQConfig; // RabbitMQ yapılandırmamız
import com.educonnect.clubservice.dto.message.AssignClubRoleMessage;
import com.educonnect.clubservice.dto.request.AddMemberRequest;
import com.educonnect.clubservice.dto.request.CreateClubRequest;
import com.educonnect.clubservice.dto.request.SubmitClubRequest;
import com.educonnect.clubservice.dto.request.UpdateMemberRoleRequest;
import com.educonnect.clubservice.dto.response.ClubDetailsDTO;
import com.educonnect.clubservice.dto.response.ClubSummaryDTO;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubRole;
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

    public ClubService(ClubRepository clubRepository,
                       ClubMembershipRepository membershipRepository,
                       RabbitTemplate rabbitTemplate,
                       MinioService minioService,
                       ClubCreationRequestRepository requestRepository) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.minioService = minioService;
        this.requestRepository = requestRepository;
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
     * Bir kulübü siler ve event-service'e RabbitMQ üzerinden haber verir.
     */
    public void deleteClub(UUID clubId) {
        // TODO: Bu işlemi yapan kullanıcının 'ROLE_ADMIN' olduğunu kontrol et.

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found with id: " + clubId));

        // 1. Önce kulübün tüm üyeliklerini sil (Veritabanı bütünlüğü için)
        List<ClubMembership> members = membershipRepository.findByClubId(clubId);
        membershipRepository.deleteAll(members);

        // 2. Kulübün kendisini sil
        clubRepository.delete(club);

        // 3. RabbitMQ ile event-service'e haber ver (konuştuğumuz gibi)
        // "Bu kulübün etkinliklerini iptal et"
        String routingKey = "club.deleted"; // (event-service bu anahtarı dinleyecek)
        rabbitTemplate.convertAndSend(ClubRabbitMQConfig.EXCHANGE_NAME, routingKey, clubId.toString());

        System.out.println("Club deleted and 'club.deleted' message sent for clubId: " + clubId);
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
}
