package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.dto.response.PositionTermResponse;
import com.educonnect.clubservice.service.ClubPositionHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs")
@PreAuthorize("isAuthenticated()")
public class ClubPositionHistoryController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubPositionHistoryService positionHistoryService;

    public ClubPositionHistoryController(ClubPositionHistoryService positionHistoryService) {
        this.positionHistoryService = positionHistoryService;
    }

    @GetMapping("/{clubId}/position-history")
    public ResponseEntity<List<PositionTermResponse>> clubHistory(@PathVariable UUID clubId,
                                                                  @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(positionHistoryService.historyOf(clubId, UUID.fromString(userIdHeader)));
    }

    @GetMapping("/{clubId}/past-presidents")
    public ResponseEntity<List<MemberDTO>> pastPresidents(@PathVariable UUID clubId) {
        return ResponseEntity.ok(positionHistoryService.pastPresidents(clubId));
    }

    @GetMapping("/my-positions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<PositionTermResponse>> myPositions(@RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(positionHistoryService.positionsOf(UUID.fromString(userIdHeader)));
    }
}
