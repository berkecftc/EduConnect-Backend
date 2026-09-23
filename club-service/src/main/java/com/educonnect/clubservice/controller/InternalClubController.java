package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.response.MemberDTO;
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

    public InternalClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping("/{clubId}/members/ids")
    public ResponseEntity<List<UUID>> getClubMemberIds(@PathVariable UUID clubId) {
        List<UUID> memberIds = clubService.getClubDetails(clubId).getMembers().stream()
                .map(MemberDTO::getStudentId)
                .toList();
        return ResponseEntity.ok(memberIds);
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
}
