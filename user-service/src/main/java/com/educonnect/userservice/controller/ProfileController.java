package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.request.UpdateUserProfileRequest;
import com.educonnect.userservice.dto.response.ArchivedAcademicianDTO;
import com.educonnect.userservice.dto.response.ArchivedStudentDTO;
import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.dto.response.UserProfileResponseDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.educonnect.userservice.service.ProfileAggregationService;
import com.educonnect.userservice.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileAggregationService profileAggregationService;
    private final ObjectMapper objectMapper;

    public ProfileController(ProfileService profileService,
                             ProfileAggregationService profileAggregationService,
                             ObjectMapper objectMapper) {
        this.profileService = profileService;
        this.profileAggregationService = profileAggregationService;
        this.objectMapper = objectMapper;
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
     * Giriş yapmış kullanıcının profil bilgilerini günceller.
     */
    @PutMapping(value = "/profile/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProfileById(
            @PathVariable UUID userId,
            @RequestBody UpdateUserProfileRequest request,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            if (!isSelfUpdate(userId, userIdHeader)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("You can only update your own profile.");
            }

            UserProfileResponse updatedProfile = profileService.updateUserProfile(userId, request);
            return ResponseEntity.ok(updatedProfile);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: " + e.getMessage());
        }
    }

    /**
     * FormData ile gelen profil güncellemelerini ve opsiyonel profil resmi yüklemesini destekler.
     */
    @PutMapping(value = "/profile/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProfileByIdMultipart(
            @PathVariable UUID userId,
            @RequestParam(required = false) String firstName,
            @RequestParam(name = "first_name", required = false) String firstNameSnake,
            @RequestParam(required = false) String lastName,
            @RequestParam(name = "last_name", required = false) String lastNameSnake,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String officeNumber,
            @RequestParam(name = "office_number", required = false) String officeNumberSnake,
            @RequestPart(value = "data", required = false) String dataJson,
            @RequestPart(value = "profileData", required = false) String profileDataJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            if (!isSelfUpdate(userId, userIdHeader)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("You can only update your own profile.");
            }

            UpdateUserProfileRequest bodyPart = parseRequestPartJson(dataJson, profileDataJson);

            String normalizedFirstName = coalesceNonBlank(firstName, firstNameSnake, bodyPart.getFirstName());
            String normalizedLastName = coalesceNonBlank(lastName, lastNameSnake, bodyPart.getLastName());
            String normalizedBio = coalesceNonBlank(bio, bodyPart.getBio());
            String normalizedDepartment = coalesceNonBlank(department, bodyPart.getDepartment());
            String normalizedTitle = coalesceNonBlank(title, bodyPart.getTitle());
            String normalizedOfficeNumber = coalesceNonBlank(officeNumber, officeNumberSnake, bodyPart.getOfficeNumber());

            MultipartFile uploadFile = firstNonEmptyFile(file, profileImage, profilePicture);

            boolean hasTextUpdate = normalizedFirstName != null || normalizedLastName != null || normalizedBio != null
                    || normalizedDepartment != null || normalizedTitle != null || normalizedOfficeNumber != null;
            boolean hasFile = uploadFile != null;

            if (!hasTextUpdate && !hasFile) {
                // Save butonu degisiklik olmadan tetiklenirse no-op olarak basarili don.
                return ResponseEntity.ok(profileService.getUserProfile(userId));
            }

            if (hasTextUpdate) {
                UpdateUserProfileRequest request = new UpdateUserProfileRequest();
                request.setFirstName(normalizedFirstName);
                request.setLastName(normalizedLastName);
                request.setBio(normalizedBio);
                request.setDepartment(normalizedDepartment);
                request.setTitle(normalizedTitle);
                request.setOfficeNumber(normalizedOfficeNumber);
                profileService.updateUserProfile(userId, request);
            }

            if (hasFile) {
                profileService.uploadProfilePicture(userId, uploadFile);
            }

            return ResponseEntity.ok(profileService.getUserProfile(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: " + e.getMessage());
        }
    }

    /**
     * API Composition yaklaşımı ile profile + gamification + recent posts verisini birleştirir.
     */
    @GetMapping("/profile/{userId}/aggregated")
    public ResponseEntity<UserProfileResponseDTO> getAggregatedProfileById(@PathVariable UUID userId) {
        UserProfileResponseDTO profile = profileAggregationService.getAggregatedUserProfile(userId);
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

    private boolean isSelfUpdate(UUID userId, String userIdHeader) {
        UUID authenticatedUserId = UUID.fromString(userIdHeader);
        return Objects.equals(userId, authenticatedUserId);
    }

    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String coalesceNonBlank(String first, String second, String third) {
        String candidate = coalesceNonBlank(first, second);
        return candidate != null ? candidate : normalizeBlankToNull(third);
    }

    private String coalesceNonBlank(String first, String second) {
        String normalizedFirst = normalizeBlankToNull(first);
        return normalizedFirst != null ? normalizedFirst : normalizeBlankToNull(second);
    }

    private UpdateUserProfileRequest parseRequestPartJson(String dataJson, String profileDataJson) {
        String json = coalesceNonBlank(dataJson, profileDataJson);
        if (json == null) {
            return new UpdateUserProfileRequest();
        }
        try {
            return objectMapper.readValue(json, UpdateUserProfileRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid profile data payload.", e);
        }
    }

    private MultipartFile firstNonEmptyFile(MultipartFile... candidates) {
        for (MultipartFile candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

}