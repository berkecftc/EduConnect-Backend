package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseClient;
import com.educonnect.assignmentservice.client.CourseInternalClient;
import com.educonnect.assignmentservice.client.InternalUserClient;
import com.educonnect.assignmentservice.client.UserClient;
import com.educonnect.assignmentservice.dto.*;
import com.educonnect.assignmentservice.event.AssignmentNotificationEvent;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.publisher.AssignmentProducer;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    public static final String STUDENT_ASSIGNMENTS = "studentAssignments";

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final MinioService minioService;
    private final CourseClient courseClient;
    private final CourseInternalClient courseInternalClient;
    private final UserClient userClient;
    private final AssignmentProducer assignmentProducer;
    private final InternalUserClient internalUserClient;
    private final CacheManager cacheManager;
    private final AssignmentFiles assignmentFiles;

    public AssignmentService(AssignmentRepository repo, SubmissionRepository subRepo,
                             MinioService minio, CourseClient client, CourseInternalClient internalClient,
                             UserClient userClient, AssignmentProducer producer,
                             InternalUserClient internalUserClient, CacheManager cacheManager,
                             AssignmentFiles assignmentFiles) {
        this.internalUserClient = internalUserClient;
        this.assignmentRepository = repo;
        this.submissionRepository = subRepo;
        this.minioService = minio;
        this.courseClient = client;
        this.courseInternalClient = internalClient;
        this.userClient = userClient;
        this.assignmentProducer = producer;
        this.cacheManager = cacheManager;
        this.assignmentFiles = assignmentFiles;
    }

    @CacheEvict(value = STUDENT_ASSIGNMENTS, allEntries = true)
    public AssignmentResponse createAssignment(AssignmentRequest request, MultipartFile file) {
        if (request.getDueDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Son teslim tarihi zorunludur.");
        }
        // 1. Önce böyle bir ders var mı diye Course Service'e sor
        Map<String, Object> courseData;
        try {
            courseData = courseClient.getCourseById(request.getCourseId());
        } catch (Exception e) {
            throw new RuntimeException("Ders bulunamadı! Geçersiz Course ID.");
        }

        // 2. Dosya yükle (varsa)
        String fileUrl = null;
        if (file != null && !file.isEmpty()) {
            fileUrl = minioService.uploadFile(file);
        }

        // 3. Kaydet
        Assignment assignment = new Assignment();
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setDueDate(request.getDueDate());
        assignment.setCourseId(request.getCourseId());
        assignment.setFileUrl(minioService.normalizeToFullUrl(fileUrl));

        Assignment saved = assignmentRepository.save(assignment);

        // 4. Kayıtlı öğrencilere bildirim gönder
        sendAssignmentNotification(request.getCourseId(), courseData, saved);

        return mapToResponse(saved);
    }

    /**
     * Ödev oluşturulduğunda kayıtlı öğrencilere RabbitMQ ile bildirim gönderir.
     */
    private void sendAssignmentNotification(UUID courseId, Map<String, Object> courseData, Assignment assignment) {
        try {
            // Course-service'ten kayıtlı öğrenci ID'lerini çek
            List<UUID> enrolledStudentIds = courseInternalClient.getEnrolledStudentIds(courseId);

            if (enrolledStudentIds == null || enrolledStudentIds.isEmpty()) {
                log.info("Derste kayıtlı öğrenci yok, ödev bildirimi gönderilmedi.");
                return;
            }

            String courseTitle = courseData.get("title") != null ? courseData.get("title").toString() : "Bilinmeyen Ders";
            String courseCode = courseData.get("code") != null ? courseData.get("code").toString() : "";

            AssignmentNotificationEvent event = new AssignmentNotificationEvent(
                    courseId,
                    courseTitle,
                    courseCode,
                    "ASSIGNMENT",
                    assignment.getTitle(),
                    assignment.getDescription(),
                    enrolledStudentIds
            );

            assignmentProducer.sendAssignmentCreatedNotification(event);
            log.info("Ödev bildirimi gönderildi: {} öğrenciye -> {} ({})",
                    enrolledStudentIds.size(), assignment.getTitle(), courseTitle);
        } catch (Exception e) {
            log.error("Ödev bildirimi gönderilemedi: {}", e.getMessage());
        }
    }

    public List<AssignmentResponse> getAssignmentsByCourse(UUID courseId) {
        return assignmentRepository.findByCourseId(courseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = STUDENT_ASSIGNMENTS, allEntries = true)
    public void deleteAssignment(UUID id) {
        List<String> files = assignmentRepository.findById(id).map(assignment -> assignmentFiles.of(List.of(assignment)))
                .orElse(List.of());
        assignmentRepository.deleteById(id);
        minioService.deleteFilesAfterCommit(files);
    }

    // ÖĞRENCİ ÖDEV TESLİMİ (Deadline kontrolü + tekrar teslim)
    @CacheEvict(value = STUDENT_ASSIGNMENTS, key = "#studentId")
    public AssignmentSubmission submitAssignment(UUID assignmentId, UUID studentId, MultipartFile file) {
        // Ödev var mı kontrol et
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Ödev bulunamadı"));

        // Deadline kontrolü
        boolean isLate = assignment.getDueDate() != null && LocalDateTime.now().isAfter(assignment.getDueDate());

        // Daha önce teslim var mı kontrol et (tekrar teslim)
        Optional<AssignmentSubmission> existingSubmission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        existingSubmission.ifPresent(previous -> {
            if (previous.getGrade() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Notlanmış bir teslim değiştirilemez.");
            }
            if (isLate) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Son teslim tarihi geçtikten sonra teslim değiştirilemez.");
            }
        });

        // Dosya yükle
        String fileUrl = null;
        if (file != null && !file.isEmpty()) {
            fileUrl = minioService.uploadFile(file);
        }

        if (existingSubmission.isPresent()) {
            // Mevcut teslimi güncelle
            AssignmentSubmission submission = existingSubmission.get();
            String previousFileUrl = submission.getSubmissionFileUrl();
            submission.setSubmissionFileUrl(minioService.normalizeToFullUrl(fileUrl));
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setLate(isLate);
            submission.setGrade(null);
            submission.setFeedback(null);
            AssignmentSubmission saved = submissionRepository.save(submission);
            if (previousFileUrl != null && !previousFileUrl.equals(saved.getSubmissionFileUrl())) {
                minioService.deleteFilesAfterCommit(List.of(previousFileUrl));
            }
            return saved;
        } else {
            // Yeni teslim oluştur
            AssignmentSubmission submission = new AssignmentSubmission(
                    assignmentId,
                    studentId,
                    minioService.normalizeToFullUrl(fileUrl),
                    isLate
            );
            return submissionRepository.save(submission);
        }
    }

    // AKADEMİSYEN NOT VERME
    public void gradeSubmission(UUID submissionId, Integer grade, String feedback) {
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Teslim bulunamadı"));

        if (grade != null && (grade < 0 || grade > 100)) {
            throw new RuntimeException("Not 0-100 arasında olmalıdır");
        }

        submission.setGrade(grade);
        submission.setFeedback(feedback);
        submissionRepository.save(submission);
        evictStudentAssignments(submission.getStudentId());
    }

    private void evictStudentAssignments(UUID studentId) {
        try {
            Cache cache = cacheManager.getCache(STUDENT_ASSIGNMENTS);
            if (cache != null) {
                cache.evict(studentId);
            }
        } catch (RuntimeException e) {
            log.warn("{} cache temizlenemedi: {}", STUDENT_ASSIGNMENTS, e.getMessage());
        }
    }

    // BİR DERSE AİT TÜM TESLİMLERİ GETİR (Akademisyen için)
    public List<SubmissionSummaryDTO> getSubmissionsByCourse(UUID courseId) {
        // Önce bu derse ait tüm ödevleri bul
        List<UUID> assignmentIds = assignmentRepository.findByCourseId(courseId).stream()
                .map(Assignment::getId)
                .toList();
        if (assignmentIds.isEmpty()) {
            return List.of();
        }
        return toSubmissionSummaries(submissionRepository.findByAssignmentIdIn(assignmentIds));
    }

    // BİR ÖDEVE AİT TÜM TESLİMLERİ GETİR (Akademisyen için)
    public List<SubmissionSummaryDTO> getSubmissionsByAssignment(UUID assignmentId) {
        return toSubmissionSummaries(submissionRepository.findByAssignmentId(assignmentId));
    }

    // ÖĞRENCİNİN TÜM ÖDEVLERİNİ GETİR (Teslim durumuyla birlikte)
    @Cacheable(value = STUDENT_ASSIGNMENTS, key = "#studentId")
    public List<MyAssignmentDTO> getStudentAssignments(UUID studentId) {
        // Öğrencinin teslimleri
        List<AssignmentSubmission> submissions = submissionRepository.findByStudentId(studentId);

        Set<UUID> courseIds = new HashSet<>();
        try {
            courseIds.addAll(courseInternalClient.getActiveCourseIds(studentId));
        } catch (Exception e) {
            log.warn("Could not fetch active courses of student {}: {}", studentId, e.getMessage());
        }
        List<UUID> submittedAssignmentIds = submissions.stream().map(AssignmentSubmission::getAssignmentId).distinct().toList();
        if (!submittedAssignmentIds.isEmpty()) {
            assignmentRepository.findAllById(submittedAssignmentIds).forEach(a -> courseIds.add(a.getCourseId()));
        }
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<Assignment> allAssignments = assignmentRepository.findByCourseIdIn(courseIds);

        return allAssignments.stream().map(assignment -> {
            normalizeAssignmentFileUrlIfNeeded(assignment);

            MyAssignmentDTO dto = new MyAssignmentDTO();
            dto.setId(assignment.getId());
            dto.setTitle(assignment.getTitle());
            dto.setDescription(assignment.getDescription());
            dto.setDueDate(assignment.getDueDate());
            dto.setCourseId(assignment.getCourseId());
            dto.setFileUrl(assignment.getFileUrl());

            // Bu ödeve ait teslim var mı?
            submissions.stream()
                    .filter(sub -> sub.getAssignmentId().equals(assignment.getId()))
                    .findFirst()
                    .ifPresent(submission -> {
                        normalizeSubmissionFileUrlIfNeeded(submission);

                        MySubmissionDTO subDto = new MySubmissionDTO();
                        subDto.setSubmissionId(submission.getId());
                        subDto.setSubmittedAt(submission.getSubmittedAt());
                        subDto.setGrade(submission.getGrade());
                        subDto.setFeedback(submission.getFeedback());
                        subDto.setLate(submission.isLate());
                        dto.setSubmission(subDto);
                    });

            return dto;
        }).collect(Collectors.toList());
    }

    private AssignmentResponse mapToResponse(Assignment a) {
        normalizeAssignmentFileUrlIfNeeded(a);

        AssignmentResponse res = new AssignmentResponse();
        res.setId(a.getId());
        res.setTitle(a.getTitle());
        res.setDescription(a.getDescription());
        res.setDueDate(a.getDueDate());
        res.setCourseId(a.getCourseId());
        res.setFileUrl(a.getFileUrl());
        return res;
    }

    private List<SubmissionSummaryDTO> toSubmissionSummaries(List<AssignmentSubmission> submissions) {
        Map<UUID, UserClient.UserProfileDTO> students = studentsById(
                submissions.stream().map(AssignmentSubmission::getStudentId).toList());
        return submissions.stream()
                .map(submission -> mapToSubmissionSummary(submission, students.get(submission.getStudentId())))
                .collect(Collectors.toList());
    }

    private Map<UUID, UserClient.UserProfileDTO> studentsById(List<UUID> studentIds) {
        List<UUID> ids = studentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return internalUserClient.getUsersByIds(ids).stream()
                    .filter(profile -> profile != null && profile.getId() != null)
                    .collect(Collectors.toMap(UserClient.UserProfileDTO::getId, Function.identity(), (first, second) -> first));
        } catch (Exception e) {
            log.warn("Could not fetch {} student profiles: {}", ids.size(), e.getMessage());
            return Map.of();
        }
    }

    private SubmissionSummaryDTO mapToSubmissionSummary(AssignmentSubmission submission, UserClient.UserProfileDTO userProfile) {
        normalizeSubmissionFileUrlIfNeeded(submission);

        SubmissionSummaryDTO dto = new SubmissionSummaryDTO();
        dto.setSubmissionId(submission.getId());
        dto.setStudentId(submission.getStudentId());
        dto.setSubmissionFileUrl(submission.getSubmissionFileUrl());
        dto.setSubmittedAt(submission.getSubmittedAt());
        dto.setGrade(submission.getGrade());
        dto.setLate(submission.isLate());

        if (userProfile != null) {
            dto.setStudentName(userProfile.getFirstName() + " " + userProfile.getLastName());
            dto.setStudentNumber(userProfile.getStudentNumber());
        } else {
            dto.setStudentName("Bilinmeyen Öğrenci");
            dto.setStudentNumber("N/A");
        }

        return dto;
    }

    private void normalizeAssignmentFileUrlIfNeeded(Assignment assignment) {
        String currentUrl = assignment.getFileUrl();
        String normalizedUrl = minioService.normalizeToFullUrl(currentUrl);

        if (normalizedUrl != null && !normalizedUrl.equals(currentUrl)) {
            assignment.setFileUrl(normalizedUrl);
            assignmentRepository.save(assignment);
        }
    }

    private void normalizeSubmissionFileUrlIfNeeded(AssignmentSubmission submission) {
        String currentUrl = submission.getSubmissionFileUrl();
        String normalizedUrl = minioService.normalizeToFullUrl(currentUrl);

        if (normalizedUrl != null && !normalizedUrl.equals(currentUrl)) {
            submission.setSubmissionFileUrl(normalizedUrl);
            submissionRepository.save(submission);
        }
    }

    // DOSYA İNDİRME
    public Resource downloadFile(String fileUrl) {
        InputStream inputStream = minioService.downloadFile(fileUrl);
        return new InputStreamResource(inputStream);
    }

    public String getOriginalFileName(String fileUrl) {
        return minioService.extractOriginalFileName(fileUrl);
    }
}