package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseClient;
import com.educonnect.assignmentservice.dto.*;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final MinioService minioService;
    private final CourseClient courseClient;

    public AssignmentService(AssignmentRepository repo, SubmissionRepository subRepo, MinioService minio, CourseClient client) {
        this.assignmentRepository = repo;
        this.submissionRepository = subRepo;
        this.minioService = minio;
        this.courseClient = client;
    }

    public AssignmentResponse createAssignment(AssignmentRequest request, MultipartFile file) {
        // 1. Önce böyle bir ders var mı diye Course Service'e sor
        try {
            courseClient.getCourseById(request.getCourseId());
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
        assignment.setFileUrl(fileUrl);

        Assignment saved = assignmentRepository.save(assignment);
        return mapToResponse(saved);
    }

    public List<AssignmentResponse> getAssignmentsByCourse(UUID courseId) {
        return assignmentRepository.findByCourseId(courseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void deleteAssignment(UUID id) {
        assignmentRepository.deleteById(id);
    }

    // ÖĞRENCİ ÖDEV TESLİMİ (Deadline kontrolü + tekrar teslim)
    @CacheEvict(value = "studentAssignments", key = "#studentId")
    public AssignmentSubmission submitAssignment(UUID assignmentId, UUID studentId, MultipartFile file) {
        // Ödev var mı kontrol et
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Ödev bulunamadı"));

        // Deadline kontrolü
        boolean isLate = LocalDateTime.now().isAfter(assignment.getDueDate());

        // Dosya yükle
        String fileUrl = null;
        if (file != null && !file.isEmpty()) {
            fileUrl = minioService.uploadFile(file);
        }

        // Daha önce teslim var mı kontrol et (tekrar teslim)
        Optional<AssignmentSubmission> existingSubmission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);

        if (existingSubmission.isPresent()) {
            // Mevcut teslimi güncelle
            AssignmentSubmission submission = existingSubmission.get();
            submission.setSubmissionFileUrl(fileUrl);
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setLate(isLate);
            // Not ve feedback'i sıfırlama (akademisyen yeniden notlandıracak)
            return submissionRepository.save(submission);
        } else {
            // Yeni teslim oluştur
            AssignmentSubmission submission = new AssignmentSubmission(assignmentId, studentId, fileUrl, isLate);
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

        // Öğrencinin cache'ini temizle
        // @CacheEvict kullanılamaz çünkü studentId metod parametresi değil
        // Manuel cache eviction yapılabilir ama şimdilik basit tutuyoruz
    }

    // BİR DERSE AİT TÜM TESLİMLERİ GETİR (Akademisyen için)
    public List<SubmissionSummaryDTO> getSubmissionsByCourse(UUID courseId) {
        // Önce bu derse ait tüm ödevleri bul
        List<Assignment> assignments = assignmentRepository.findByCourseId(courseId);

        // Her ödevin teslimlerini topla
        return assignments.stream()
                .flatMap(assignment -> submissionRepository.findByAssignmentId(assignment.getId()).stream())
                .map(this::mapToSubmissionSummary)
                .collect(Collectors.toList());
    }

    // BİR ÖDEVE AİT TÜM TESLİMLERİ GETİR (Akademisyen için)
    public List<SubmissionSummaryDTO> getSubmissionsByAssignment(UUID assignmentId) {
        return submissionRepository.findByAssignmentId(assignmentId).stream()
                .map(this::mapToSubmissionSummary)
                .collect(Collectors.toList());
    }

    // ÖĞRENCİNİN TÜM ÖDEVLERİNİ GETİR (Teslim durumuyla birlikte)
    @Cacheable(value = "studentAssignments", key = "#studentId")
    public List<MyAssignmentDTO> getStudentAssignments(UUID studentId) {
        // Öğrencinin teslimleri
        List<AssignmentSubmission> submissions = submissionRepository.findByStudentId(studentId);

        // Tüm ödevleri al (TODO: Öğrencinin kayıtlı olduğu derslere göre filtreleme yapılabilir)
        List<Assignment> allAssignments = assignmentRepository.findAll();

        return allAssignments.stream().map(assignment -> {
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
        AssignmentResponse res = new AssignmentResponse();
        res.setId(a.getId());
        res.setTitle(a.getTitle());
        res.setDescription(a.getDescription());
        res.setDueDate(a.getDueDate());
        res.setCourseId(a.getCourseId());
        res.setFileUrl(a.getFileUrl());
        return res;
    }

    private SubmissionSummaryDTO mapToSubmissionSummary(AssignmentSubmission submission) {
        SubmissionSummaryDTO dto = new SubmissionSummaryDTO();
        dto.setSubmissionId(submission.getId());
        dto.setStudentId(submission.getStudentId());
        dto.setSubmissionFileUrl(submission.getSubmissionFileUrl());
        dto.setSubmittedAt(submission.getSubmittedAt());
        dto.setGrade(submission.getGrade());
        dto.setLate(submission.isLate());

        // Student name'i user-service'den çekilebilir (opsiyonel)
        dto.setStudentName("Student-" + submission.getStudentId().toString().substring(0, 8));

        return dto;
    }
}