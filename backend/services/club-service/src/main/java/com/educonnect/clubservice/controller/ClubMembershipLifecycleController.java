package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.response.MyClubMembershipDTO;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.service.ClubQueryService;
import com.educonnect.clubservice.service.MembershipRenewalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs")
@PreAuthorize("hasRole('STUDENT')")
public class ClubMembershipLifecycleController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final MembershipRenewalService renewalService;
    private final ClubQueryService clubQueryService;

    public ClubMembershipLifecycleController(MembershipRenewalService renewalService, ClubQueryService clubQueryService) {
        this.renewalService = renewalService;
        this.clubQueryService = clubQueryService;
    }

    @PostMapping("/{clubId}/membership/renew")
    public ResponseEntity<MyClubMembershipDTO> renew(@PathVariable UUID clubId,
                                                     @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        UUID studentId = UUID.fromString(userIdHeader);
        ClubMembership membership = renewalService.renew(clubId, studentId);
        return ResponseEntity.ok(clubQueryService.getMembershipHistory(studentId).stream()
                .filter(dto -> dto.getClubId().equals(membership.getClubId()))
                .findFirst()
                .orElseThrow());
    }

    @GetMapping("/my-memberships/history")
    public ResponseEntity<List<MyClubMembershipDTO>> history(@RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(clubQueryService.getMembershipHistory(UUID.fromString(userIdHeader)));
    }
}
