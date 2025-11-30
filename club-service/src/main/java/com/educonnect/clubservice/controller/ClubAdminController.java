package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.CreateClubRequest;
import com.educonnect.clubservice.dto.request.UpdateClubRequest;
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

    // Kulüp Silme (ve Event Service'e bildirme)
    @DeleteMapping("/{clubId}")
    @PreAuthorize("hasRole('ADMIN')") // Sadece Admin rolü
    public ResponseEntity<String> deleteClub(
            @PathVariable UUID clubId,
            @RequestHeader(value = "X-Authenticated-User-Id", required = false) String userId) {

        log.info("Deleting club: {}, requested by userId: {}", clubId, userId);

        clubService.deleteClub(clubId);

        log.info("Club deleted successfully: {}", clubId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Club deleted successfully.");
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

    @PutMapping("/{clubId}")
    @PreAuthorize("hasRole('ADMIN')") // Veya kulüp başkanı
    public ResponseEntity<Club> updateClub(@PathVariable UUID clubId, @RequestBody UpdateClubRequest request) {
        return ResponseEntity.ok(clubService.updateClub(clubId, request));
    }


}