package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.service.ProfileViewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/internal/profiles")
public class InternalProfileController {

    private final ProfileViewService profileViewService;

    public InternalProfileController(ProfileViewService profileViewService) {
        this.profileViewService = profileViewService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(profileViewService.getProfileForService(userId));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<UserProfileResponse>> getProfiles(@RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(profileViewService.getProfilesForService(userIds));
    }

    @GetMapping("/by-student-number/{studentNumber}")
    public ResponseEntity<UserProfileResponse> getByStudentNumber(@PathVariable String studentNumber) {
        return ResponseEntity.ok(profileViewService.getStudentByStudentNumberForService(studentNumber));
    }
}
