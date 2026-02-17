package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.response.ArchivedAcademicianDTO;
import com.educonnect.userservice.dto.response.ArchivedStudentDTO;
import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    /**
     * Öğrenci numarasına göre öğrenci profil bilgilerini getirir.
     * Club-service gibi diğer servisler tarafından kullanılır.
     * @param studentNumber Öğrenci numarası
     * @return Öğrenci profil bilgileri
     */
    @GetMapping("/by-student-number/{studentNumber}")
    public ResponseEntity<UserProfileResponse> getProfileByStudentNumber(
            @PathVariable String studentNumber) {
        try {
            UserProfileResponse profile = profileService.getStudentByStudentNumber(studentNumber);
            return ResponseEntity.ok(profile);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
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

    /**
     * Admin tarafından öğrenciyi siler (arşivler).
     * Sadece ADMIN rolü erişebilir.
     */
    @DeleteMapping("/students/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteStudent(
            @PathVariable UUID userId,
            @RequestParam(required = false) String reason) {
        try {
            profileService.archiveStudent(userId, reason);
            return ResponseEntity.ok("Öğrenci başarıyla arşivlendi ve aktif tablodan kaldırıldı.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Admin tarafından akademisyeni siler (arşivler).
     * Sadece ADMIN rolü erişebilir.
     */
    @DeleteMapping("/academicians/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteAcademician(
            @PathVariable UUID userId,
            @RequestParam(required = false) String reason) {
        try {
            profileService.archiveAcademician(userId, reason);
            return ResponseEntity.ok("Akademisyen başarıyla arşivlendi ve aktif tablodan kaldırıldı.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Arşivlenmiş öğrencileri listeler.
     * Sadece ADMIN rolü erişebilir.
     */
    @GetMapping("/students/archived")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ArchivedStudentDTO>> getAllArchivedStudents() {
        List<ArchivedStudentDTO> archivedStudents = profileService.getAllArchivedStudents();
        return ResponseEntity.ok(archivedStudents);
    }

    /**
     * Arşivlenmiş akademisyenleri listeler.
     * Sadece ADMIN rolü erişebilir.
     */
    @GetMapping("/academicians/archived")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ArchivedAcademicianDTO>> getAllArchivedAcademicians() {
        List<ArchivedAcademicianDTO> archivedAcademicians = profileService.getAllArchivedAcademicians();
        return ResponseEntity.ok(archivedAcademicians);
    }

}