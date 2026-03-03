package com.educonnect.courseservice.controller;

import com.educonnect.courseservice.dto.*;
import com.educonnect.courseservice.exception.UnauthorizedCourseAccessException;
import com.educonnect.courseservice.service.CourseAnnouncementService;
import com.educonnect.courseservice.service.CourseApplicationService;
import com.educonnect.courseservice.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;
    private final CourseApplicationService applicationService;
    private final CourseAnnouncementService announcementService;

    public CourseController(CourseService courseService,
                            CourseApplicationService applicationService,
                            CourseAnnouncementService announcementService) {
        this.courseService = courseService;
        this.applicationService = applicationService;
        this.announcementService = announcementService;
    }

    // ===================== DERS CRUD =====================

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getAll() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @GetMapping("/instructor/{id}")
    public ResponseEntity<List<CourseResponse>> getByInstructor(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getCoursesByInstructor(id));
    }

    // DERS OLUŞTURMA (JSON + RESİM)
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<CourseResponse> create(
            @RequestPart("course") @Valid CourseRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId
    ) {
        // Header'dan gelen kullanıcı ID ile body'deki instructorId eşleşmeli
        UUID authUserId = UUID.fromString(authenticatedUserId);
        if (!authUserId.equals(request.getInstructorId())) {
            throw new UnauthorizedCourseAccessException("Sadece kendi adınıza ders oluşturabilirsiniz");
        }
        return ResponseEntity.ok(courseService.createCourse(request, file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    // ===================== ÖĞRENCİ KAYIT (DOĞRUDAN - Akademisyen) =====================

    @PostMapping("/{courseId}/enroll-student")
    public ResponseEntity<String> enrollStudent(
            @PathVariable UUID courseId,
            @RequestBody EnrollStudentRequest request,
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        try {
            UUID instructorId = UUID.fromString(instructorIdHeader);
            courseService.enrollStudent(courseId, request.getStudentId(), instructorId);
            return ResponseEntity.ok("Öğrenci başarıyla kursa kaydedildi");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // ÖĞRENCİNİN KAYITLI OLDUĞU KURSLARI GETİR
    @GetMapping("/my-courses")
    public ResponseEntity<List<EnrolledCourseDTO>> getMyCourses(
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        UUID studentId = UUID.fromString(studentIdHeader);
        return ResponseEntity.ok(courseService.getStudentCourses(studentId));
    }

    // ÖĞRENCİ KURSTAN ÇIK
    @DeleteMapping("/{courseId}/withdraw")
    public ResponseEntity<String> withdrawFromCourse(
            @PathVariable UUID courseId,
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        try {
            UUID studentId = UUID.fromString(studentIdHeader);
            courseService.withdrawStudent(courseId, studentId);
            return ResponseEntity.ok("Kurstan başarıyla çıkıldı");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // AKADEMİSYENİN DERSLERİNİ GETİR (Öğrenci sayılarıyla + kapasite + bekleyen başvuru sayısı)
    @GetMapping("/instructor/me/courses")
    public ResponseEntity<List<InstructorCourseDTO>> getMyInstructorCourses(
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        UUID instructorId = UUID.fromString(instructorIdHeader);
        return ResponseEntity.ok(courseService.getInstructorCourses(instructorId));
    }

    // ===================== BAŞVURU SİSTEMİ =====================

    // ÖĞRENCİ DERSE BAŞVURU YAPAR
    @PostMapping("/{courseId}/apply")
    public ResponseEntity<?> applyToCourse(
            @PathVariable UUID courseId,
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        try {
            UUID studentId = UUID.fromString(studentIdHeader);
            CourseApplicationResponse response = applicationService.applyToCourse(courseId, studentId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // HOCA BEKLEYEN BAŞVURULARI LİSTELER (Tarih sırasına göre - FCFS)
    @GetMapping("/{courseId}/applications/pending")
    public ResponseEntity<?> getPendingApplications(
            @PathVariable UUID courseId,
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        try {
            UUID instructorId = UUID.fromString(instructorIdHeader);
            List<CourseApplicationResponse> applications = applicationService.getPendingApplications(courseId, instructorId);
            return ResponseEntity.ok(applications);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // HOCA TEK TEK BAŞVURU ONAYLAR
    @PutMapping("/applications/{applicationId}/approve")
    public ResponseEntity<?> approveApplication(
            @PathVariable UUID applicationId,
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        try {
            UUID instructorId = UUID.fromString(instructorIdHeader);
            CourseApplicationResponse response = applicationService.approveApplication(applicationId, instructorId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // HOCA BAŞVURU REDDEDER
    @PutMapping("/applications/{applicationId}/reject")
    public ResponseEntity<?> rejectApplication(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) RejectApplicationRequest request,
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        try {
            UUID instructorId = UUID.fromString(instructorIdHeader);
            String reason = (request != null) ? request.getRejectionReason() : null;
            CourseApplicationResponse response = applicationService.rejectApplication(applicationId, instructorId, reason);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // ÖĞRENCİ KENDİ BAŞVURULARINI GÖRÜNTÜLER
    @GetMapping("/my-applications")
    public ResponseEntity<List<CourseApplicationResponse>> getMyApplications(
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        UUID studentId = UUID.fromString(studentIdHeader);
        return ResponseEntity.ok(applicationService.getMyApplications(studentId));
    }

    // ===================== DUYURU SİSTEMİ =====================

    // HOCA DUYURU OLUŞTURUR (Kayıtlı öğrencilere bildirim + email gider)
    @PostMapping("/{courseId}/announcements")
    public ResponseEntity<?> createAnnouncement(
            @PathVariable UUID courseId,
            @RequestBody AnnouncementRequest request,
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        try {
            UUID instructorId = UUID.fromString(instructorIdHeader);
            AnnouncementResponse response = announcementService.createAnnouncement(courseId, request, instructorId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // BİR DERSE AİT DUYURULARI LİSTELER
    @GetMapping("/{courseId}/announcements")
    public ResponseEntity<List<AnnouncementResponse>> getAnnouncements(@PathVariable UUID courseId) {
        return ResponseEntity.ok(announcementService.getAnnouncementsByCourse(courseId));
    }

    // DUYURU SİLER
    @DeleteMapping("/announcements/{announcementId}")
    public ResponseEntity<String> deleteAnnouncement(
            @PathVariable UUID announcementId,
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        try {
            UUID instructorId = UUID.fromString(instructorIdHeader);
            announcementService.deleteAnnouncement(announcementId, instructorId);
            return ResponseEntity.ok("Duyuru başarıyla silindi");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // ===================== KAYITLI ÖĞRENCİ LİSTESİ =====================

    // HOCA - BİR DERSE KAYITLI ÖĞRENCİLERİ GETİR (Detaylı bilgiyle)
    @GetMapping("/{courseId}/enrolled-students")
    public ResponseEntity<?> getEnrolledStudents(
            @PathVariable UUID courseId,
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        try {
            UUID instructorId = UUID.fromString(instructorIdHeader);
            List<EnrolledStudentDTO> students = courseService.getEnrolledStudents(courseId, instructorId);
            return ResponseEntity.ok(students);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // DİĞER SERVİSLER İÇİN - KAYITLI ÖĞRENCİ ID LİSTESİ
    @GetMapping("/{courseId}/enrolled-students/ids")
    public ResponseEntity<List<UUID>> getEnrolledStudentIds(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.getEnrolledStudentIds(courseId));
    }

    // ===================== DOSYA İNDİRME =====================

    @GetMapping("/files/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("url") String fileUrl) {
        try {
            Resource resource = courseService.downloadFile(fileUrl);
            String fileName = courseService.getOriginalFileName(fileUrl);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}