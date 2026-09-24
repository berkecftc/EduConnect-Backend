package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.response.ClubAccessResponse;
import com.educonnect.clubservice.dto.response.ClubCatalogEntry;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.service.ClubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs/internal")
public class InternalClubController {

    private final ClubService clubService;
    private final ClubAuthorizationService clubAuthorizationService;

    public InternalClubController(ClubService clubService, ClubAuthorizationService clubAuthorizationService) {
        this.clubService = clubService;
        this.clubAuthorizationService = clubAuthorizationService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<ClubCatalogEntry>> getClubCatalog() {
        return ResponseEntity.ok(clubService.getClubCatalog());
    }

    @GetMapping("/{clubId}/members/ids")
    public ResponseEntity<List<UUID>> getClubMemberIds(@PathVariable UUID clubId) {
        return ResponseEntity.ok(clubService.getActiveMemberIds(clubId));
    }

    @GetMapping("/{clubId}/is-member/{studentId}")
    public ResponseEntity<Boolean> isStudentMemberOfClub(@PathVariable UUID clubId, @PathVariable UUID studentId) {
        return ResponseEntity.ok(clubService.isStudentMemberOfClub(clubId, studentId));
    }

    @GetMapping("/{clubId}/advisor-id")
    public ResponseEntity<UUID> getClubAdvisorId(@PathVariable UUID clubId) {
        return ResponseEntity.ok(clubService.getClubAdvisorId(clubId));
    }

    @GetMapping("/by-advisor/{advisorId}/ids")
    public ResponseEntity<List<UUID>> getClubIdsByAdvisor(@PathVariable UUID advisorId) {
        return ResponseEntity.ok(clubService.getClubIdsByAdvisorId(advisorId));
    }

    @GetMapping("/{clubId}/access/{userId}")
    public ResponseEntity<ClubAccessResponse> getAccess(@PathVariable UUID clubId, @PathVariable UUID userId) {
        return ResponseEntity.ok(ClubAccessResponse.from(clubAuthorizationService.accessOf(clubId, userId)));
    }

    @GetMapping("/users/{userId}/access")
    public ResponseEntity<List<ClubAccessResponse>> getUserAccess(@PathVariable UUID userId) {
        return ResponseEntity.ok(clubAuthorizationService.accessesOf(userId).stream()
                .map(ClubAccessResponse::from)
                .toList());
    }
}
