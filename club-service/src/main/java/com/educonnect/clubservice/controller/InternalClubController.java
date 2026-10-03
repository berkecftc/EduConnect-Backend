package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.response.ClubAccessResponse;
import com.educonnect.clubservice.dto.response.ClubCatalogEntry;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.service.ClubQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.educonnect.clubservice.service.ClubLeadershipService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs/internal")
public class InternalClubController {

    private final ClubQueryService clubQueryService;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubLeadershipService leadershipService;

    public InternalClubController(ClubQueryService clubQueryService, ClubAuthorizationService clubAuthorizationService,
                                  ClubLeadershipService leadershipService) {
        this.clubQueryService = clubQueryService;
        this.clubAuthorizationService = clubAuthorizationService;
        this.leadershipService = leadershipService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<ClubCatalogEntry>> getClubCatalog() {
        return ResponseEntity.ok(clubQueryService.getClubCatalog());
    }

    @GetMapping("/{clubId}/members/ids")
    public ResponseEntity<List<UUID>> getClubMemberIds(@PathVariable UUID clubId) {
        return ResponseEntity.ok(clubQueryService.getActiveMemberIds(clubId));
    }

    @GetMapping("/{clubId}/is-member/{studentId}")
    public ResponseEntity<Boolean> isStudentMemberOfClub(@PathVariable UUID clubId, @PathVariable UUID studentId) {
        return ResponseEntity.ok(clubQueryService.isStudentMemberOfClub(clubId, studentId));
    }

    @GetMapping("/managers/{viewerId}/students/{studentId}")
    public ResponseEntity<Map<String, Boolean>> managesStudent(@PathVariable UUID viewerId, @PathVariable UUID studentId) {
        return ResponseEntity.ok(Map.of("related", clubQueryService.managesStudent(viewerId, studentId)));
    }

    @GetMapping("/{clubId}/leader-ids")
    public ResponseEntity<List<UUID>> getClubLeaderIds(@PathVariable UUID clubId) {
        return ResponseEntity.ok(leadershipService.currentLeaderOf(clubId).map(List::of).orElse(List.of()));
    }

    @GetMapping("/{clubId}/advisor-id")
    public ResponseEntity<UUID> getClubAdvisorId(@PathVariable UUID clubId) {
        return ResponseEntity.ok(clubQueryService.getClubAdvisorId(clubId));
    }

    @GetMapping("/by-name")
    public ResponseEntity<UUID> getClubIdByName(@RequestParam String name) {
        return ResponseEntity.ok(clubQueryService.getClubIdByName(name));
    }

    @GetMapping("/by-advisor/{advisorId}/ids")
    public ResponseEntity<List<UUID>> getClubIdsByAdvisor(@PathVariable UUID advisorId) {
        return ResponseEntity.ok(clubQueryService.getClubIdsByAdvisorId(advisorId));
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
