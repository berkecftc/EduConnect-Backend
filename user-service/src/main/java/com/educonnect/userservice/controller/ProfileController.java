package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Belirli bir kullanıcının profil bilgilerini getirir.
     */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<UserProfileResponse> getProfileById(@PathVariable UUID userId) {
        UserProfileResponse profile = profileService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    // --- YENİ ENDPOINT: Profil Resmi Yükleme ---
    // Bu endpoint, giriş yapmış kullanıcının KENDİ resmini yüklemesi içindir.
    @PostMapping(value = "/me/profile-picture", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadMyProfilePicture(
            @RequestParam("file") MultipartFile file,
            // API Gateway'den (AuthenticationFilter) gelen kullanıcı ID'si
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty.");
            }

            UUID userId = UUID.fromString(userIdHeader);

            // Servisi çağır (Yükleme, DB güncelleme ve Cache temizleme burada yapılır)
            String fileUrl = profileService.uploadProfilePicture(userId, file);

            return ResponseEntity.ok(fileUrl); // Yeni resmin yolunu (objectName) döndür

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

}