package com.educonnect.courseservice.controller;

import com.educonnect.courseservice.dto.*;
import com.educonnect.courseservice.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) { this.courseService = courseService; }

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getAll() { return ResponseEntity.ok(courseService.getAllCourses()); }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getById(@PathVariable UUID id) { return ResponseEntity.ok(courseService.getCourseById(id)); }

    @GetMapping("/instructor/{id}")
    public ResponseEntity<List<CourseResponse>> getByInstructor(@PathVariable UUID id) { return ResponseEntity.ok(courseService.getCoursesByInstructor(id)); }

    // DERS OLUŞTURMA (JSON + RESİM)
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<CourseResponse> create(
            @RequestPart("course") CourseRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(courseService.createCourse(request, file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    // ÖĞRENCİYİ KURSA KAYDET (Akademisyen)
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

    // AKADEMİSYENİN DERSLERİNİ GETİR (Öğrenci sayılarıyla)
    @GetMapping("/instructor/me/courses")
    public ResponseEntity<List<InstructorCourseDTO>> getMyInstructorCourses(
            @RequestHeader("X-Authenticated-User-Id") String instructorIdHeader
    ) {
        UUID instructorId = UUID.fromString(instructorIdHeader);
        return ResponseEntity.ok(courseService.getInstructorCourses(instructorId));
    }
}