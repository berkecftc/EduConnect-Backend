package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.dto.request.AddMemberRequest;
import com.educonnect.clubservice.dto.request.SubmitClubRequest;
import com.educonnect.clubservice.dto.request.UpdateMemberRoleRequest;
import com.educonnect.clubservice.dto.response.ClubDetailsDTO;
import com.educonnect.clubservice.dto.response.ClubSummaryDTO;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.dto.response.MyClubMembershipDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.service.ClubService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clubs") // Public rota
public class ClubController {

    private final ClubService clubService;
    private final ClubRepository clubRepository;

    // --- MANUEL CONSTRUCTOR ---
    // Lombok'un @RequiredArgsConstructor ile arka planda yaptığı iş budur
    public ClubController(ClubService clubService, ClubRepository clubRepository) {
        this.clubService = clubService;
        this.clubRepository = clubRepository;
    }

    // Tüm Kulüpleri Listele (Özet Bilgi)
    @GetMapping
    public ResponseEntity<List<ClubSummaryDTO>> getAllClubs() {
        return ResponseEntity.ok(clubService.getAllClubs());
    }

    // Tek Bir Kulübün Detaylarını Getir (Üyelerle Birlikte)
    @GetMapping("/{clubId}")
    public ResponseEntity<ClubDetailsDTO> getClubDetails(@PathVariable UUID clubId) {
        return ResponseEntity.ok(clubService.getClubDetails(clubId));
    }

    /**
     * Giriş yapmış öğrencinin, URL'de belirtilen kulübe katılması için istek.
     * @param clubId URL'den gelen kulüp ID'si
     * @param userIdHeader API Gateway tarafından JWT token'dan eklenen kullanıcı ID'si
     * @return Başarılı katılım mesajı
     */
    @PostMapping("/{clubId}/join")
    @PreAuthorize("isAuthenticated()") // Sadece giriş yapmış kullanıcılar (öğrenciler vb.)
    public ResponseEntity<String> joinClub(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            UUID studentId = UUID.fromString(userIdHeader);
            clubService.joinClub(clubId, studentId);

            return ResponseEntity.ok("Successfully joined the club.");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid user id format.");
        } catch (IllegalStateException e) {
            // "Zaten üye" hatasını yakala
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (RuntimeException e) {
            // "Kulüp bulunamadı" hatasını yakala
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/{clubId}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')") // Sadece Admin veya Kulüp Yetkilisi
    public ResponseEntity<ClubMembership> addMember(
            @PathVariable UUID clubId,
            @RequestBody AddMemberRequest request
    ) {
        // TODO: (İleri Seviye) İstek atan kullanıcının (token'dan gelen)
        // bu 'clubId'nin gerçekten yetkilisi olup olmadığını kontrol et.

        try {
            ClubMembership newMember = clubService.addMemberToClub(clubId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(newMember);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null); // Zaten üye
        }
    }

    /**
     * Kulüp Yetkilisi: Mevcut üyenin rolünü günceller.
     * (UpdateMemberRoleRequest DTO'sunu kullanır)
     */
    @PutMapping("/{clubId}/members/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')") // Sadece Admin veya Kulüp Yetkilisi
    public ResponseEntity<ClubMembership> updateMemberRole(
            @PathVariable UUID clubId,
            @PathVariable UUID studentId,
            @RequestBody UpdateMemberRoleRequest request
    ) {
        // TODO: (İleri Seviye) İstek atan kullanıcının yetki kontrolü

        try {
            ClubMembership updatedMember = clubService.updateMemberRole(clubId, studentId, request);
            return ResponseEntity.ok(updatedMember);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null); // Üyelik bulunamadı
        }
    }

    // --- Öğrencinin kulüpten ayrılması ---
    @DeleteMapping("/{clubId}/leave")
    @PreAuthorize("isAuthenticated()") // Giriş yapmış herkes kendi üyeliğini silebilir
    public ResponseEntity<String> leaveClub(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            UUID studentId = UUID.fromString(userIdHeader);
            clubService.leaveClub(clubId, studentId);
            return ResponseEntity.ok("Successfully left the club.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid user id format.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * Giriş yapmış bir kulüp yetkilisinin (veya Admin'in)
     * ilgili kulübün logosunu yüklemesi/güncellemesi için endpoint.
     * @param clubId URL'den gelen kulüp ID'si
     * @param file Form-data olarak gönderilen dosya
     * @param userIdHeader API Gateway tarafından JWT token'dan eklenen kullanıcı ID'si
     * @return Yüklenen dosyanın adı (objectName)
     */
    @PostMapping(value = "/{clubId}/logo", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')") // Sadece Admin veya Kulüp Yetkilisi
    public ResponseEntity<String> uploadClubLogo(
            @PathVariable UUID clubId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty.");
        }

        try {
            UUID requestingStudentId = UUID.fromString(userIdHeader);

            // Servis katmanı hem yetkiyi kontrol edecek hem de yüklemeyi yapacak
            String objectName = clubService.updateClubLogo(clubId, file, requestingStudentId);

            return ResponseEntity.ok(objectName);

        } catch (ResponseStatusException e) {
            // Service katmanından fırlatılan FORBIDDEN veya NOT_FOUND hatalarını yakala
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading file: " + e.getMessage());
        }
    }

    @PostMapping("/request-creation")
    @PreAuthorize("isAuthenticated()") // Herhangi bir öğrenci yapabilir
    public ResponseEntity<String> requestClubCreation(
            @RequestBody SubmitClubRequest request,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID studentId = UUID.fromString(userIdHeader);
        clubService.submitClubCreationRequest(request, studentId);
        return ResponseEntity.ok("Club creation request submitted. Pending admin approval.");
    }

    /**
     * İsme göre kulüp arama (Event Service gibi diğer servisler kullanacak).
     * Örn: GET /api/clubs/search?name=Yapay Zeka Kulübü
     */
    @GetMapping("/search")
    public ResponseEntity<ClubSummaryDTO> getClubByName(@RequestParam String name) {
        Club club = clubRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Club not found with name: " + name));

        return ResponseEntity.ok(new ClubSummaryDTO(club.getId(), club.getName(), club.getLogoUrl()));
    }

    /**
     * Notification Service için: Bir kulübün tüm üyelerinin ID'lerini döner.
     * (Sadece servisler arası iletişim için kullanılacağından güvenliği basit tutabiliriz veya internal yapabiliriz)
     */
    @GetMapping("/{clubId}/members/ids")
    public ResponseEntity<List<UUID>> getClubMemberIds(@PathVariable UUID clubId) {
        // ClubDetailsDTO'dan veya direkt repository'den çekebiliriz
        // Repository'de 'findStudentIdsByClubId' gibi bir metot yoksa, stream ile çevirelim:
        List<UUID> memberIds = clubService.getClubDetails(clubId).getMembers().stream()
                .map(MemberDTO::getStudentId)
                .collect(Collectors.toList());

        return ResponseEntity.ok(memberIds);
    }

    // ÖĞRENCİNİN KULÜP ÜYELİKLERİNİ GETİR
    @GetMapping("/my-memberships")
    public ResponseEntity<List<MyClubMembershipDTO>> getMyMemberships(
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        UUID studentId = UUID.fromString(studentIdHeader);
        return ResponseEntity.ok(clubService.getStudentClubMemberships(studentId));
    }

    // ==================== CLUB OFFICIAL DASHBOARD ENDPOINTS ====================

    /**
     * Bir kulübün yönetim kurulunu getirir.
     * Başkan, Başkan Yardımcısı, Yönetim Kurulu Üyeleri bilgilerini döner.
     * User-service'den isim bilgisi ile zenginleştirilmiş.
     */
    @GetMapping("/{clubId}/board-members")
    public ResponseEntity<List<MemberDTO>> getClubBoardMembers(@PathVariable UUID clubId) {
        List<MemberDTO> boardMembers = clubService.getClubBoardMembers(clubId);
        return ResponseEntity.ok(boardMembers);
    }

    /**
     * Öğrencinin kulüp üyesi olup olmadığını kontrol eder.
     * Event-service gibi diğer servisler tarafından kullanılır.
     * GET /api/clubs/{clubId}/is-member/{studentId}
     */
    @GetMapping("/{clubId}/is-member/{studentId}")
    public ResponseEntity<Boolean> isStudentMemberOfClub(
            @PathVariable UUID clubId,
            @PathVariable UUID studentId
    ) {
        boolean isMember = clubService.isStudentMemberOfClub(clubId, studentId);
        return ResponseEntity.ok(isMember);
    }

    /**
     * Kulüp yetkilisinin yönetim kurulunda olduğu tüm kulüpleri getirir.
     * ROLE_CLUB_OFFICIAL, ROLE_VICE_PRESIDENT veya ROLE_BOARD_MEMBER rolüne sahip olduğu kulüpler.
     * Cache: 5 dakika TTL
     */
    @GetMapping("/my-managed-clubs")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')")
    public ResponseEntity<List<MyClubMembershipDTO>> getMyManagedClubs(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID userId = UUID.fromString(userIdHeader);
        List<MyClubMembershipDTO> managedClubs = clubService.getManagedClubs(userId);
        return ResponseEntity.ok(managedClubs);
    }

    /**
     * Bir kulübün danışman akademisyen ID'sini döndürür.
     * Event-service tarafından etkinlik onayı için kullanılır (Feign Client).
     */
    @GetMapping("/{clubId}/advisor-id")
    public ResponseEntity<UUID> getClubAdvisorId(@PathVariable UUID clubId) {
        UUID advisorId = clubService.getClubAdvisorId(clubId);
        return ResponseEntity.ok(advisorId);
    }

    /**
     * Bir danışman akademisyenin sorumlu olduğu kulüplerin ID listesini döndürür.
     * Event-service tarafından bekleyen etkinlikleri listelemek için kullanılır (Feign Client).
     */
    @GetMapping("/by-advisor/{advisorId}/ids")
    public ResponseEntity<List<UUID>> getClubIdsByAdvisor(@PathVariable UUID advisorId) {
        List<UUID> clubIds = clubService.getClubIdsByAdvisorId(advisorId);
        return ResponseEntity.ok(clubIds);
    }
}
