package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.request.UpdateUserProfileRequest;
import com.educonnect.userservice.dto.response.ArchivedAcademicianDTO;
import com.educonnect.userservice.dto.response.ArchivedStudentDTO;
import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.dto.response.UserProfileResponseDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.educonnect.common.security.IdentityHeaders;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ForbiddenException;
import com.educonnect.userservice.service.ProfileService;
import com.educonnect.userservice.service.ProfileViewService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileViewService profileViewService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ProfileController(ProfileService profileService,
                             ProfileViewService profileViewService,
                             ObjectMapper objectMapper,
                             Validator validator) {
        this.profileService = profileService;
        this.profileViewService = profileViewService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * Belirli bir kullanıcının profil bilgilerini getirir.
     */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<UserProfileResponse> getProfileById(
            @PathVariable UUID userId,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) UUID viewerId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String viewerRoles) {
        return ResponseEntity.ok(profileViewService.getProfile(userId, viewerId, viewerRoles));
    }

    /**
     * Giriş yapmış kullanıcının profil bilgilerini günceller.
     */
    @PutMapping(value = "/profile/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProfileById(
            @PathVariable UUID userId,
            @RequestBody @Valid UpdateUserProfileRequest request,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        requireSelfUpdate(userId, userIdHeader);

        UserProfileResponse updatedProfile = profileService.updateUserProfile(userId, request);
        return ResponseEntity.ok(updatedProfile);
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
            @RequestParam(required = false) String officeHours,
            @RequestPart(value = "data", required = false) String dataJson,
            @RequestPart(value = "profileData", required = false) String profileDataJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        requireSelfUpdate(userId, userIdHeader);

        UpdateUserProfileRequest bodyPart = parseRequestPartJson(dataJson, profileDataJson);

        String normalizedFirstName = coalesceNonBlank(firstName, firstNameSnake, bodyPart.getFirstName());
        String normalizedLastName = coalesceNonBlank(lastName, lastNameSnake, bodyPart.getLastName());
        String normalizedBio = coalesceNonBlank(bio, bodyPart.getBio());
        String normalizedDepartment = coalesceNonBlank(department, bodyPart.getDepartment());
        String normalizedTitle = coalesceNonBlank(title, bodyPart.getTitle());
        String normalizedOfficeNumber = coalesceNonBlank(officeNumber, officeNumberSnake, bodyPart.getOfficeNumber());
        String normalizedOfficeHours = coalesceNonBlank(officeHours, bodyPart.getOfficeHours());

        MultipartFile uploadFile = firstNonEmptyFile(file, profileImage, profilePicture);

        boolean hasTextUpdate = normalizedFirstName != null || normalizedLastName != null || normalizedBio != null
                || normalizedDepartment != null || normalizedTitle != null || normalizedOfficeNumber != null
                || normalizedOfficeHours != null;
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
            request.setOfficeHours(normalizedOfficeHours);
            validate(request);
            profileService.updateUserProfile(userId, request);
        }

        if (hasFile) {
            profileService.uploadProfilePicture(userId, uploadFile);
        }

        return ResponseEntity.ok(profileService.getUserProfile(userId));
    }

    /**
     * API Composition yaklaşımı ile profile + gamification + recent posts verisini birleştirir.
     */
    @GetMapping("/profile/{userId}/aggregated")
    public ResponseEntity<UserProfileResponseDTO> getAggregatedProfileById(
            @PathVariable UUID userId,
            @RequestHeader(value = IdentityHeaders.USER_ID, required = false) UUID viewerId,
            @RequestHeader(value = IdentityHeaders.USER_ROLES, required = false) String viewerRoles) {
        return ResponseEntity.ok(profileViewService.getAggregatedProfile(userId, viewerId, viewerRoles));
    }

    // --- YENİ ENDPOINT: Profil Resmi Yükleme ---
    // Bu endpoint, giriş yapmış kullanıcının KENDİ resmini yüklemesi içindir.
    @PostMapping(value = "/me/profile-picture", consumes = "multipart/form-data", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> uploadMyProfilePicture(
            @RequestParam("file") MultipartFile file,
            // API Gateway'den (AuthenticationFilter) gelen kullanıcı ID'si
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty.");
        }

        UUID userId = UUID.fromString(userIdHeader);

        // Servisi çağır (Yükleme, DB güncelleme ve Cache temizleme burada yapılır)
        String fileUrl = profileService.uploadProfilePicture(userId, file);

        return ResponseEntity.ok(fileUrl); // Yeni resmin yolunu (objectName) döndür
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

    private void requireSelfUpdate(UUID userId, String userIdHeader) {
        UUID authenticatedUserId = UUID.fromString(userIdHeader);
        if (!Objects.equals(userId, authenticatedUserId)) {
            throw new ForbiddenException("You can only update your own profile.");
        }
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
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid profile data payload.", e);
        }
    }

    private void validate(UpdateUserProfileRequest request) {
        Set<ConstraintViolation<UpdateUserProfileRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
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
