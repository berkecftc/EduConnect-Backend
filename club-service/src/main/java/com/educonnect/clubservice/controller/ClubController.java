package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.dto.request.SubmitClubRequest;
import com.educonnect.clubservice.dto.request.UpdateMemberRoleRequest;
import com.educonnect.clubservice.dto.response.ClubDetailsDTO;
import com.educonnect.clubservice.dto.response.ClubSummaryDTO;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.dto.response.MyClubMembershipDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.service.ClubService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

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
    public ResponseEntity<ClubDetailsDTO> getClubDetails(
            @PathVariable UUID clubId,
            @RequestHeader(value = "X-Authenticated-User-Id", required = false) String userIdHeader
    ) {
        UUID viewerId = userIdHeader != null ? UUID.fromString(userIdHeader) : null;
        return ResponseEntity.ok(clubService.getClubDetails(clubId, viewerId));
    }

    @PostMapping("/{clubId}/join")
    public ResponseEntity<String> joinClub(@PathVariable UUID clubId) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body("Kulübe doğrudan katılım kapatıldı. Üyelik için POST /api/clubs/{clubId}/membership-requests kullanın.");
    }

    @PostMapping("/{clubId}/members")
    public ResponseEntity<String> addMember(@PathVariable UUID clubId) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body("Doğrudan üye ekleme kapatıldı. Üyelik başvurusu ve görev değişikliği talebi akışlarını kullanın.");
    }

    /**
     * Kulüp Yetkilisi: Mevcut üyenin rolünü günceller.
     * (UpdateMemberRoleRequest DTO'sunu kullanır)
     *
     * @deprecated Bu endpoint artık kullanılmamalıdır. Görev değişiklikleri danışman onayına tabidir.
     *             Görev atamak için POST /api/clubs/{clubId}/role-change-requests,
     *             Görevden almak için DELETE /api/clubs/{clubId}/members/{studentId}/role kullanın.
     */
    @Deprecated
    @PutMapping("/{clubId}/members/{studentId}")
    public ResponseEntity<String> updateMemberRole(
            @PathVariable UUID clubId,
            @PathVariable UUID studentId,
            @RequestBody UpdateMemberRoleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body("Bu endpoint artık kullanılmamaktadır. Görev değişiklikleri danışman onayına tabidir. " +
                        "Görev atamak için POST /api/clubs/{clubId}/role-change-requests, " +
                        "Görevden almak için DELETE /api/clubs/{clubId}/members/{studentId}/role kullanın.");
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
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
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
    @PreAuthorize("isAuthenticated()")
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
        return ResponseEntity.ok("Club creation request submitted. Pending advisor approval.");
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
     * Kulüp yetkilisinin yönetim kurulunda olduğu tüm kulüpleri getirir.
     * ROLE_CLUB_OFFICIAL, ROLE_VICE_PRESIDENT veya ROLE_BOARD_MEMBER rolüne sahip olduğu kulüpler.
     * Cache: 5 dakika TTL
     */
    @GetMapping("/my-managed-clubs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MyClubMembershipDTO>> getMyManagedClubs(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID userId = UUID.fromString(userIdHeader);
        List<MyClubMembershipDTO> managedClubs = clubService.getManagedClubs(userId);
        return ResponseEntity.ok(managedClubs);
    }
}
