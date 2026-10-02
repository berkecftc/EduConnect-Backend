package com.educonnect.userservice.service;

import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.userservice.dto.request.UpdateUserProfileRequest;
import com.educonnect.userservice.dto.response.AcademicPlacement;
import com.educonnect.userservice.dto.response.ArchivedAcademicianDTO;
import com.educonnect.userservice.dto.response.ArchivedStudentDTO;
import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.models.AcademicTitle;
import com.educonnect.userservice.models.Academician;
import com.educonnect.userservice.models.ArchivedAcademician;
import com.educonnect.userservice.models.ArchivedStudent;
import com.educonnect.userservice.models.ProfileChangeRequest;
import com.educonnect.userservice.models.Student;
import com.educonnect.userservice.repository.AcademicianRepository;
import com.educonnect.userservice.repository.ArchivedAcademicianRepository;
import com.educonnect.userservice.repository.ArchivedStudentRepository;
import com.educonnect.userservice.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileService.class);
    private static final String USER_PROFILE_CACHE = "userProfileV3";
    public static final String STUDENT_AFFILIATION = "STUDENT";
    public static final String ACADEMICIAN_AFFILIATION = "ACADEMICIAN";
    private static final String USER_PROFILE_BY_STUDENT_NUMBER_CACHE = "userProfileByStudentNumberV2";

    private final StudentRepository studentRepository;
    private final AcademicianRepository academicianRepository;
    private final ArchivedStudentRepository archivedStudentRepository;
    private final ArchivedAcademicianRepository archivedAcademicianRepository;
    private final MinioService minioService;
    private final GamificationEventPublisher gamificationEventPublisher;
    private final CacheManager cacheManager;
    private final AcademicCatalogService catalogService;

    // Elle constructor ekleyelim
    public ProfileService(StudentRepository studentRepository,
                         AcademicianRepository academicianRepository,
                         ArchivedStudentRepository archivedStudentRepository,
                         ArchivedAcademicianRepository archivedAcademicianRepository,
                         MinioService minioService,
                         GamificationEventPublisher gamificationEventPublisher,
                         CacheManager cacheManager,
                         AcademicCatalogService catalogService) {
        this.studentRepository = studentRepository;
        this.academicianRepository = academicianRepository;
        this.archivedStudentRepository = archivedStudentRepository;
        this.archivedAcademicianRepository = archivedAcademicianRepository;
        this.minioService = minioService;
        this.gamificationEventPublisher = gamificationEventPublisher;
        this.cacheManager = cacheManager;
        this.catalogService = catalogService;
    }


    @Cacheable(value = USER_PROFILE_CACHE, key = "#userId")
    public UserProfileResponse getUserProfile(UUID userId) {
        return toResponse(studentRepository.findById(userId), academicianRepository.findById(userId))
                .orElseThrow(() -> new NotFoundException("PROFILE_NOT_FOUND", "Profile not found for user ID: " + userId));
    }

    public List<UserProfileResponse> getUserProfiles(Collection<UUID> userIds) {
        Map<UUID, Student> students = new LinkedHashMap<>();
        studentRepository.findAllById(userIds).forEach(student -> students.put(student.getId(), student));
        Map<UUID, Academician> academicians = new LinkedHashMap<>();
        academicianRepository.findAllById(userIds).forEach(academician -> academicians.put(academician.getId(), academician));
        Set<UUID> ids = new LinkedHashSet<>(students.keySet());
        ids.addAll(academicians.keySet());
        List<UserProfileResponse> profiles = new ArrayList<>();
        ids.forEach(id -> toResponse(Optional.ofNullable(students.get(id)), Optional.ofNullable(academicians.get(id))).ifPresent(profiles::add));
        return profiles;
    }

    @Transactional(readOnly = false)
    @CacheEvict(value = USER_PROFILE_CACHE, key = "#userId")
    public UserProfileResponse updateUserProfile(UUID userId, UpdateUserProfileRequest request) {
        Optional<Student> studentOpt = studentRepository.findById(userId);
        Optional<Academician> academicianOpt = academicianRepository.findById(userId);
        if (studentOpt.isEmpty() && academicianOpt.isEmpty()) {
            throw new NotFoundException("PROFILE_NOT_FOUND", "Profile not found for user ID: " + userId);
        }
        requireOfficialFieldsUnchanged(request, studentOpt, academicianOpt);
        boolean wasComplete = isProfileComplete(studentOpt, academicianOpt);
        studentOpt.ifPresent(student -> {
            evictStudentNumber(student.getStudentNumber());
            if (request.getBio() != null) {
                student.setBio(request.getBio());
            }
            studentRepository.save(student);
        });
        if (academicianOpt.isPresent()) {
            Academician academician = academicianOpt.get();
            if (request.getBio() != null) {
                academician.setBio(request.getBio());
            }
            if (request.getOfficeNumber() != null) {
                academician.setOfficeNumber(request.getOfficeNumber());
            }
            if (request.getOfficeHours() != null) {
                academician.setOfficeHours(request.getOfficeHours().isBlank() ? null : request.getOfficeHours().strip());
            }
            academicianRepository.save(academician);
        }
        publishProfileCompletedIfNeeded(userId, wasComplete, isProfileComplete(studentOpt, academicianOpt));
        return toResponse(studentOpt, academicianOpt).orElseThrow();
    }

    // --- YENİ METOT: Profil Resmi Yükleme ---
    /**
     * Bir kullanıcının profil resmini günceller, MinIO'ya yükler
     * ve Redis'teki eski profil cache'ini temizler.
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = USER_PROFILE_CACHE, key = "#userId") // Başarılı olursa cache'i temizle!
    public String uploadProfilePicture(UUID userId, MultipartFile file) {

        // 1. Önce kullanıcının profilinin var olup olmadığını kontrol et
        Optional<Student> studentOpt = studentRepository.findById(userId);
        Optional<Academician> academicianOpt = academicianRepository.findById(userId);

        if (studentOpt.isEmpty() && academicianOpt.isEmpty()) {
            throw new NotFoundException("PROFILE_NOT_FOUND",
                "Profile not found for user ID: " + userId +
                ". Please make sure your account has been properly registered and profile created. " +
                "This may happen if you're using an old token or if profile creation failed."
            );
        }

        // 2. Dosyayı MinIO'ya yükle
        String objectName = minioService.uploadFile(file, userId);
        LOGGER.info("File uploaded to MinIO: {} for userId: {}", objectName, userId);

        // 3. Veritabanındaki kaydı güncelle
        boolean wasComplete = isProfileComplete(studentOpt, academicianOpt);
        studentOpt.ifPresent(student -> {
            evictStudentNumber(student.getStudentNumber());
            student.setProfileImageUrl(objectName);
            studentRepository.save(student);
        });
        academicianOpt.ifPresent(academician -> {
            academician.setProfileImageUrl(objectName);
            academicianRepository.save(academician);
        });
        LOGGER.info("Profile picture updated for userId: {}", userId);
        publishProfileCompletedIfNeeded(userId, wasComplete, isProfileComplete(studentOpt, academicianOpt));

        // 4. MinIO'daki dosya yolunu döndür
        return objectName;
    }

    /**
     * Öğrenciyi arşivleyip aktif tablodan siler.
     * @param userId Silinecek öğrencinin ID'si
     * @param reason Silme nedeni (opsiyonel)
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = USER_PROFILE_CACHE, key = "#userId")
    public void archiveStudent(UUID userId, String reason) {
        Student student = studentRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("PROFILE_NOT_FOUND", "Student not found with ID: " + userId));
        evictStudentNumber(student.getStudentNumber());

        // Arşiv kaydı oluştur
        ArchivedStudent archivedStudent = new ArchivedStudent(
            student.getId(),
            student.getFirstName(),
            student.getLastName(),
            student.getStudentNumber(),
            student.getDepartment(),
            student.getProfileImageUrl(),
            LocalDateTime.now(),
            reason
        );

        // Arşive kaydet
        archivedStudentRepository.save(archivedStudent);
        LOGGER.info("Student archived successfully. ID: {}, Name: {} {}",
            student.getId(), student.getFirstName(), student.getLastName());

        // Aktif tablodan sil
        studentRepository.delete(student);
        minioService.deleteFilesAfterCommit(List.of(Objects.toString(student.getStudentDocumentUrl(), "")));
        LOGGER.info("Student removed from active table. ID: {}", student.getId());
    }

    /**
     * Akademisyeni arşivleyip aktif tablodan siler.
     * @param userId Silinecek akademisyenin ID'si
     * @param reason Silme nedeni (opsiyonel)
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = USER_PROFILE_CACHE, key = "#userId")
    public void archiveAcademician(UUID userId, String reason) {
        Academician academician = academicianRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("PROFILE_NOT_FOUND", "Academician not found with ID: " + userId));

        // Arşiv kaydı oluştur
        ArchivedAcademician archivedAcademician = new ArchivedAcademician(
            academician.getId(),
            academician.getFirstName(),
            academician.getLastName(),
            academician.getTitle(),
            academician.getDepartment(),
            academician.getOfficeNumber(),
            academician.getProfileImageUrl(),
            LocalDateTime.now(),
            reason
        );

        // Arşive kaydet
        archivedAcademicianRepository.save(archivedAcademician);
        LOGGER.info("Academician archived successfully. ID: {}, Name: {} {}",
            academician.getId(), academician.getFirstName(), academician.getLastName());

        // Aktif tablodan sil
        academicianRepository.delete(academician);
        minioService.deleteFilesAfterCommit(List.of(Objects.toString(academician.getIdCardImageUrl(), "")));
        LOGGER.info("Academician removed from active table. ID: {}", academician.getId());
    }

    /**
     * Tüm arşivlenmiş öğrencileri listeler.
     * Sadece Admin kullanıcılar erişebilir.
     * @return Arşivlenmiş öğrencilerin DTO listesi
     */
    @Transactional(readOnly = true)
    public List<ArchivedStudentDTO> getAllArchivedStudents() {
        List<ArchivedStudent> archivedStudents = archivedStudentRepository.findAllByOrderByDeletedAtDesc();

        return archivedStudents.stream()
                .map(student -> new ArchivedStudentDTO(
                        student.getArchiveId(),
                        student.getOriginalId(),
                        student.getFirstName(),
                        student.getLastName(),
                        student.getStudentNumber(),
                        student.getDepartment(),
                        student.getProfileImageUrl(),
                        student.getDeletedAt(),
                        student.getDeletionReason()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Tüm arşivlenmiş akademisyenleri listeler.
     * Sadece Admin kullanıcılar erişebilir.
     * @return Arşivlenmiş akademisyenlerin DTO listesi
     */
    @Transactional(readOnly = true)
    public List<ArchivedAcademicianDTO> getAllArchivedAcademicians() {
        List<ArchivedAcademician> archivedAcademicians = archivedAcademicianRepository.findAllByOrderByDeletedAtDesc();

        return archivedAcademicians.stream()
                .map(academician -> new ArchivedAcademicianDTO(
                        academician.getArchiveId(),
                        academician.getOriginalId(),
                        academician.getFirstName(),
                        academician.getLastName(),
                        academician.getTitle(),
                        academician.getDepartment(),
                        academician.getOfficeNumber(),
                        academician.getProfileImageUrl(),
                        academician.getDeletedAt(),
                        academician.getDeletionReason()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Öğrenci numarasına göre öğrenci profili getirir.
     * @param studentNumber Öğrenci numarası
     * @return Öğrenci profil bilgileri     */
    @Cacheable(value = USER_PROFILE_BY_STUDENT_NUMBER_CACHE, key = "#studentNumber")
    public UserProfileResponse getStudentByStudentNumber(String studentNumber) {
        Student student = studentRepository.findByStudentNumber(studentNumber)
                .orElseThrow(() -> new NotFoundException("PROFILE_NOT_FOUND",
                        "Bu öğrenci numarasına sahip kullanıcı bulunamadı: " + studentNumber));

        return toResponse(Optional.of(student), academicianRepository.findById(student.getId())).orElseThrow();
    }

    private Optional<UserProfileResponse> toResponse(Optional<Student> student, Optional<Academician> academician) {
        if (academician.isPresent()) {
            UserProfileResponse dto = mapToResponse(academician.get());
            student.ifPresent(s -> {
                dto.setStudentNumber(s.getStudentNumber());
                dto.setStudentStatus(s.getEnrollmentStatus());
                applyProgram(dto, s);
                dto.setAffiliations(List.of(ACADEMICIAN_AFFILIATION, STUDENT_AFFILIATION));
            });
            return Optional.of(dto);
        }
        return student.map(this::mapToResponse);
    }

    private void applyProgram(UserProfileResponse dto, Student student) {
        if (student.getProgramId() != null) {
            AcademicPlacement placement = catalogService.program(student.getProgramId());
            dto.setProgramId(placement.programId());
            dto.setProgramName(placement.programName());
            dto.setProgramLevel(placement.programLevel().name());
            dto.setFacultyName(Optional.ofNullable(dto.getFacultyName()).orElse(placement.facultyName()));
            if (dto.getDepartmentId() == null) {
                dto.setDepartmentId(placement.departmentId());
            }
        }
        dto.setEntryYear(student.getEntryYear());
        dto.setClassYear(catalogService.classYear(student.getEntryYear()));
    }

    private UserProfileResponse mapToResponse(Student student) {
        UserProfileResponse dto = new UserProfileResponse();
        dto.setId(student.getId());
        dto.setFirstName(student.getFirstName());
        dto.setLastName(student.getLastName());
        dto.setEmail(student.getEmail());
        dto.setProfileImageUrl(student.getProfileImageUrl());
        dto.setBio(student.getBio());
        dto.setDepartment(student.getDepartment());
        dto.setStudentNumber(student.getStudentNumber());
        dto.setRole("Student");
        dto.setAffiliations(List.of(STUDENT_AFFILIATION));
        dto.setStudentStatus(student.getEnrollmentStatus());
        applyProgram(dto, student);
        return dto;
    }

    private UserProfileResponse mapToResponse(Academician academician) {
        UserProfileResponse dto = new UserProfileResponse();
        dto.setId(academician.getId());
        dto.setFirstName(academician.getFirstName());
        dto.setLastName(academician.getLastName());
        dto.setEmail(academician.getEmail());
        dto.setProfileImageUrl(academician.getProfileImageUrl());
        dto.setBio(academician.getBio());
        dto.setDepartment(academician.getDepartment());
        dto.setTitle(academician.getTitle());
        dto.setOfficeNumber(academician.getOfficeNumber());
        dto.setOfficeHours(academician.getOfficeHours());
        dto.setRole("Academician");
        dto.setAffiliations(List.of(ACADEMICIAN_AFFILIATION));
        dto.setStaffStatus(academician.getEmploymentStatus());
        if (academician.getAcademicTitle() != null) {
            dto.setAcademicTitle(academician.getAcademicTitle().name());
            dto.setStaffCategory(academician.getStaffCategory().name());
        }
        if (academician.getDepartmentId() != null) {
            AcademicPlacement placement = catalogService.department(academician.getDepartmentId());
            dto.setDepartmentId(placement.departmentId());
            dto.setFacultyName(placement.facultyName());
        }
        return dto;
    }

    @Transactional(readOnly = false)
    @CacheEvict(value = USER_PROFILE_CACHE, key = "#userId")
    public void applyAffiliationStatus(UUID userId, String affiliation, String status, boolean ended, boolean accountClosing) {
        Optional<Student> student = studentRepository.findById(userId);
        Optional<Academician> academician = academicianRepository.findById(userId);
        if (STUDENT_AFFILIATION.equals(affiliation) && student.isPresent()) {
            evictStudentNumber(student.get().getStudentNumber());
            student.get().setEnrollmentStatus(status);
            studentRepository.save(student.get());
            if (ended && !accountClosing && academician.isPresent()) {
                archiveStudent(userId, "Öğrenci kaydı sona erdi: " + status);
            }
        } else if (ACADEMICIAN_AFFILIATION.equals(affiliation) && academician.isPresent()) {
            academician.get().setEmploymentStatus(status);
            academicianRepository.save(academician.get());
            if (ended && !accountClosing && student.isPresent()) {
                archiveAcademician(userId, "Personel kaydı sona erdi: " + status);
            }
        }
    }

    @Transactional(readOnly = false)
    @CacheEvict(value = USER_PROFILE_CACHE, key = "#userId")
    public void changeEmail(UUID userId, String email) {
        studentRepository.findById(userId).ifPresent(student -> {
            evictStudentNumber(student.getStudentNumber());
            student.setEmail(email);
            studentRepository.save(student);
        });
        academicianRepository.findById(userId).ifPresent(academician -> {
            academician.setEmail(email);
            academicianRepository.save(academician);
        });
    }

    @Transactional(readOnly = false)
    @CacheEvict(value = USER_PROFILE_CACHE, key = "#change.userId")
    public void applyOfficialChange(ProfileChangeRequest change) {
        Optional<Student> studentOpt = studentRepository.findById(change.getUserId());
        Optional<Academician> academicianOpt = academicianRepository.findById(change.getUserId());
        if (studentOpt.isEmpty() && academicianOpt.isEmpty()) {
            throw new NotFoundException("PROFILE_NOT_FOUND", "Profile not found for user ID: " + change.getUserId());
        }
        studentOpt.ifPresent(student -> {
            evictStudentNumber(student.getStudentNumber());
            if (change.getFirstName() != null) {
                student.setFirstName(change.getFirstName());
            }
            if (change.getLastName() != null) {
                student.setLastName(change.getLastName());
            }
            if (change.getProgramId() != null) {
                student.setProgramId(change.getProgramId());
                student.setDepartment(catalogService.program(change.getProgramId()).departmentName());
            }
            studentRepository.save(student);
        });
        academicianOpt.ifPresent(academician -> {
            if (change.getFirstName() != null) {
                academician.setFirstName(change.getFirstName());
            }
            if (change.getLastName() != null) {
                academician.setLastName(change.getLastName());
            }
            if (change.getAcademicTitle() != null) {
                academician.setAcademicTitle(change.getAcademicTitle());
            }
            if (change.getDepartmentId() != null) {
                academician.setDepartmentId(change.getDepartmentId());
                academician.setDepartment(catalogService.department(change.getDepartmentId()).departmentName());
            }
            academicianRepository.save(academician);
        });
    }

    private void requireOfficialFieldsUnchanged(UpdateUserProfileRequest request, Optional<Student> student,
                                                Optional<Academician> academician) {
        String firstName = academician.map(Academician::getFirstName).orElseGet(() -> student.map(Student::getFirstName).orElse(null));
        String lastName = academician.map(Academician::getLastName).orElseGet(() -> student.map(Student::getLastName).orElse(null));
        String department = academician.map(Academician::getDepartment).orElseGet(() -> student.map(Student::getDepartment).orElse(null));
        boolean changed = differs(request.getFirstName(), firstName)
                || differs(request.getLastName(), lastName)
                || differs(request.getDepartment(), department);
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            if (academician.isEmpty()) {
                throw new BadRequestException("TITLE_NOT_ALLOWED", "Unvan yalnız akademik personel profilinde bulunur.");
            }
            AcademicTitle current = academician.get().getAcademicTitle();
            changed |= current != null
                    ? AcademicTitle.parse(request.getTitle()).filter(current::equals).isEmpty()
                    : differs(request.getTitle(), academician.get().getTitle());
        }
        if (changed) {
            throw new BadRequestException("OFFICIAL_FIELD_LOCKED",
                    "Ad, soyad, bölüm ve unvan resmî bilgidir; değiştirmek için profil değişikliği talebi oluşturun.");
        }
    }

    private static boolean differs(String requested, String current) {
        if (requested == null || requested.isBlank()) {
            return false;
        }
        return current == null || !requested.strip().equals(current.strip());
    }

    private void publishProfileCompletedIfNeeded(UUID userId, boolean wasComplete, boolean isNowComplete) {
        if (!wasComplete && isNowComplete) {
            gamificationEventPublisher.publishProfileCompleted(userId);
            LOGGER.info("Profile completion gamification event published. userId={}", userId);
        }
    }

    private boolean isProfileComplete(Optional<Student> student, Optional<Academician> academician) {
        return academician.map(this::isAcademicianProfileComplete)
                .orElseGet(() -> student.map(this::isStudentProfileComplete).orElse(false));
    }

    private boolean isStudentProfileComplete(Student student) {
        // Only user-fillable fields count toward completion.
        // email and studentNumber are set automatically at registration.
        return hasText(student.getFirstName())
                && hasText(student.getLastName())
                && hasText(student.getBio())
                && hasText(student.getDepartment())
                && hasText(student.getProfileImageUrl());
    }

    private boolean isAcademicianProfileComplete(Academician academician) {
        // Only user-fillable fields count toward completion.
        // email and officeNumber are set automatically at registration.
        return hasText(academician.getFirstName())
                && hasText(academician.getLastName())
                && hasText(academician.getBio())
                && hasText(academician.getDepartment())
                && hasText(academician.getProfileImageUrl())
                && hasText(academician.getTitle());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void evictStudentNumber(String studentNumber) {
        if (!hasText(studentNumber)) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(USER_PROFILE_BY_STUDENT_NUMBER_CACHE);
            if (cache != null) {
                cache.evict(studentNumber);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("{} cache temizlenemedi: {}", USER_PROFILE_BY_STUDENT_NUMBER_CACHE, e.getMessage());
        }
    }
}
