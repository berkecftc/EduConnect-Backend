package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.CreateClubRequest;
import com.educonnect.clubservice.dto.request.UpdateClubRequest;
import com.educonnect.clubservice.dto.response.ArchivedClubDTO;
import com.educonnect.clubservice.dto.response.ClubAdminSummaryDto;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.service.ClubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/clubs") // Admin rotası
public class ClubAdminController {

    private static final Logger log = LoggerFactory.getLogger(ClubAdminController.class);

    private final ClubService clubService;

    @Autowired
    public ClubAdminController(ClubService clubService) {
        this.clubService = clubService;
    }

    // Yeni Kulüp Oluşturma
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") // Sadece Admin rolü
    public ResponseEntity<Club> createClub(
            @RequestBody CreateClubRequest request,
            @RequestHeader(value = "X-Authenticated-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Authenticated-User-Email", required = false) String userEmail) {

        log.info("Creating club: {}, requested by userId: {}, email: {}",
                 request.getName(), userId, userEmail);

        Club createdClub = clubService.createClub(request);

        log.info("Club created successfully with ID: {}", createdClub.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("X-Created-Club-Id", createdClub.getId().toString())
                .body(createdClub);
    }

    // Kulüp Kapatma/Arşivleme (Soft Delete)
    @DeleteMapping("/{clubId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteClub(
            @PathVariable UUID clubId,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = "X-Authenticated-User-Id", required = false) String userId) {

        log.info("Archiving club: {}, requested by admin: {}, reason: {}",
            clubId, userId, reason);

        UUID adminId = userId != null ? UUID.fromString(userId) : null;
        clubService.deleteClub(clubId, reason, adminId);

        log.info("Club archived successfully: {}", clubId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Kulüp başarıyla arşivlendi ve aktif listeden kaldırıldı.");
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClubCreationRequest>> getPendingRequests() {
        return ResponseEntity.ok(clubService.getPendingClubRequests());
    }

    @PostMapping("/requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Club> approveClubRequest(@PathVariable UUID requestId) {
        return ResponseEntity.ok(clubService.approveClubCreationRequest(requestId));
    }

    @PostMapping("/requests/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> rejectClubRequest(@PathVariable UUID requestId) {
        clubService.rejectClubCreationRequest(requestId); // Servisteki metodu çağır
        return ResponseEntity.ok("Club creation request rejected.");
    }

    @PutMapping("/{clubId}")
    @PreAuthorize("hasRole('ADMIN')") // Veya kulüp başkanı
    public ResponseEntity<Club> updateClub(@PathVariable UUID clubId, @RequestBody UpdateClubRequest request) {
        return ResponseEntity.ok(clubService.updateClub(clubId, request));
    }

    // Aktif Kulüpleri Listele
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClubAdminSummaryDto>> getAllActiveClubs() {
        return ResponseEntity.ok(clubService.getAllClubsForAdmin());
    }

    // Yönetim Kurulunu Gör
    @GetMapping("/{clubId}/board")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MemberDTO>> getBoardMembers(@PathVariable UUID clubId) {
        return ResponseEntity.ok(clubService.getClubBoardMembers(clubId));
    }

    // Başkanı Değiştir
    @PutMapping("/{clubId}/change-president")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> changePresident(@PathVariable UUID clubId, @RequestParam UUID newPresidentId) {
        clubService.changeClubPresident(clubId, newPresidentId);
        return ResponseEntity.ok("Kulüp başkanı başarıyla değiştirildi.");
    }

    // Geçmiş Başkanları Görüntüle
    @GetMapping("/{clubId}/past-presidents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MemberDTO>> getPastPresidents(@PathVariable UUID clubId) {
        return ResponseEntity.ok(clubService.getPastPresidents(clubId));
    }

    // MinIO Logo Yükleme Endpointi
    @PostMapping(value = "/{clubId}/logo", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> updateClubLogo(
            @PathVariable UUID clubId,
            @RequestParam("file") MultipartFile file) {

        // Dosya boş mu kontrolü
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Dosya seçilmedi.");
        }

        try {
            String newLogoUrl = clubService.updateClubLogoByAdmin(clubId, file);
            return ResponseEntity.ok(newLogoUrl); // Yeni MinIO URL'ini dönüyoruz
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Logo yüklenirken hata: " + e.getMessage());
        }
    }

    // Arşivlenmiş Kulüpleri Listele
    @GetMapping("/archived")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ArchivedClubDTO>> getAllArchivedClubs() {
        log.info("Fetching all archived clubs");
        List<ArchivedClubDTO> archivedClubs = clubService.getAllArchivedClubs();
        log.info("Found {} archived clubs", archivedClubs.size());
        return ResponseEntity.ok(archivedClubs);
    }


}