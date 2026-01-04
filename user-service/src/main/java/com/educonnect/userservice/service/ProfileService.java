package com.educonnect.userservice.service;

import com.educonnect.userservice.dto.response.ArchivedAcademicianDTO;
import com.educonnect.userservice.dto.response.ArchivedStudentDTO;
import com.educonnect.userservice.dto.response.UserProfileResponse;
import com.educonnect.userservice.models.Academician;
import com.educonnect.userservice.models.ArchivedAcademician;
import com.educonnect.userservice.models.ArchivedStudent;
import com.educonnect.userservice.models.Student;
import com.educonnect.userservice.Repository.AcademicianRepository;
import com.educonnect.userservice.Repository.ArchivedAcademicianRepository;
import com.educonnect.userservice.Repository.ArchivedStudentRepository;
import com.educonnect.userservice.Repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileService.class);

    private final StudentRepository studentRepository;
    private final AcademicianRepository academicianRepository;
    private final ArchivedStudentRepository archivedStudentRepository;
    private final ArchivedAcademicianRepository archivedAcademicianRepository;
    private final MinioService minioService;

    // Elle constructor ekleyelim
    public ProfileService(StudentRepository studentRepository,
                         AcademicianRepository academicianRepository,
                         ArchivedStudentRepository archivedStudentRepository,
                         ArchivedAcademicianRepository archivedAcademicianRepository,
                         MinioService minioService) {
        this.studentRepository = studentRepository;
        this.academicianRepository = academicianRepository;
        this.archivedStudentRepository = archivedStudentRepository;
        this.archivedAcademicianRepository = archivedAcademicianRepository;
        this.minioService = minioService;
    }


    @Cacheable(value = "userProfile", key = "#userId")
    public UserProfileResponse getUserProfile(UUID userId) {

        Optional<Student> studentOpt = studentRepository.findById(userId);
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            return mapToResponse(student);
        }

        Optional<Academician> academicianOpt = academicianRepository.findById(userId);
        if (academicianOpt.isPresent()) {
            Academician academician = academicianOpt.get();
            return mapToResponse(academician);
        }

        throw new RuntimeException("Profile not found for user ID: " + userId);
    }

    // --- YENİ METOT: Profil Resmi Yükleme ---
    /**
     * Bir kullanıcının profil resmini günceller, MinIO'ya yükler
     * ve Redis'teki eski profil cache'ini temizler.
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = "userProfile", key = "#userId") // Başarılı olursa cache'i temizle!
    public String uploadProfilePicture(UUID userId, MultipartFile file) {

        // 1. Önce kullanıcının profilinin var olup olmadığını kontrol et
        Optional<Student> studentOpt = studentRepository.findById(userId);
        Optional<Academician> academicianOpt = academicianRepository.findById(userId);

        if (studentOpt.isEmpty() && academicianOpt.isEmpty()) {
            throw new RuntimeException(
                "Profile not found for user ID: " + userId +
                ". Please make sure your account has been properly registered and profile created. " +
                "This may happen if you're using an old token or if profile creation failed."
            );
        }

        // 2. Dosyayı MinIO'ya yükle
        String objectName = minioService.uploadFile(file, userId);
        LOGGER.info("File uploaded to MinIO: {} for userId: {}", objectName, userId);

        // 3. Veritabanındaki kaydı güncelle
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            LOGGER.info("Updating student profile. Old profileImageUrl: {}, New: {}",
                student.getProfileImageUrl(), objectName);
            student.setProfileImageUrl(objectName);
            Student saved = studentRepository.save(student);
            LOGGER.info("Student profile saved. Current profileImageUrl in DB: {}",
                saved.getProfileImageUrl());
        } else {
            Academician academician = academicianOpt.get();
            LOGGER.info("Updating academician profile. Old profileImageUrl: {}, New: {}",
                academician.getProfileImageUrl(), objectName);
            academician.setProfileImageUrl(objectName);
            Academician saved = academicianRepository.save(academician);
            LOGGER.info("Academician profile saved. Current profileImageUrl in DB: {}",
                saved.getProfileImageUrl());
        }

        // 4. MinIO'daki dosya yolunu döndür
        return objectName;
    }

    /**
     * Öğrenciyi arşivleyip aktif tablodan siler.
     * @param userId Silinecek öğrencinin ID'si
     * @param reason Silme nedeni (opsiyonel)
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = "userProfile", key = "#userId")
    public void archiveStudent(UUID userId, String reason) {
        Student student = studentRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Student not found with ID: " + userId));

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
        LOGGER.info("Student removed from active table. ID: {}", student.getId());
    }

    /**
     * Akademisyeni arşivleyip aktif tablodan siler.
     * @param userId Silinecek akademisyenin ID'si
     * @param reason Silme nedeni (opsiyonel)
     */
    @Transactional(readOnly = false)
    @CacheEvict(value = "userProfile", key = "#userId")
    public void archiveAcademician(UUID userId, String reason) {
        Academician academician = academicianRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Academician not found with ID: " + userId));

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


    private UserProfileResponse mapToResponse(Student student) {
        UserProfileResponse dto = new UserProfileResponse();
        dto.setId(student.getId());
        dto.setFirstName(student.getFirstName());
        dto.setLastName(student.getLastName());
        dto.setEmail(student.getEmail());
        dto.setProfileImageUrl(student.getProfileImageUrl());
        dto.setDepartment(student.getDepartment());
        dto.setStudentNumber(student.getStudentNumber());
        dto.setRole("Student");
        return dto;
    }

    private UserProfileResponse mapToResponse(Academician academician) {
        UserProfileResponse dto = new UserProfileResponse();
        dto.setId(academician.getId());
        dto.setFirstName(academician.getFirstName());
        dto.setLastName(academician.getLastName());
        dto.setProfileImageUrl(academician.getProfileImageUrl());
        dto.setDepartment(academician.getDepartment());
        dto.setTitle(academician.getTitle());
        dto.setRole("Academician");
        return dto;
    }
}