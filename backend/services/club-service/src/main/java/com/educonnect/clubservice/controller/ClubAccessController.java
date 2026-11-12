package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.response.ClubAccessResponse;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs")
public class ClubAccessController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubAuthorizationService clubAuthorizationService;

    public ClubAccessController(ClubAuthorizationService clubAuthorizationService) {
        this.clubAuthorizationService = clubAuthorizationService;
    }

    @GetMapping("/my-access")
    public ResponseEntity<List<ClubAccessResponse>> myAccess(@RequestHeader(USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(clubAuthorizationService.accessesOf(userId).stream()
                .map(ClubAccessResponse::from)
                .toList());
    }

    @GetMapping("/{clubId}/my-access")
    public ResponseEntity<ClubAccessResponse> myAccess(@PathVariable UUID clubId,
                                                       @RequestHeader(USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(ClubAccessResponse.from(clubAuthorizationService.accessOf(clubId, userId)));
    }
}
