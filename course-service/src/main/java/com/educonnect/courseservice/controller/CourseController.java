package com.educonnect.courseservice.controller;

import com.educonnect.courseservice.dto.CourseRequest;
import com.educonnect.courseservice.dto.CourseResponse;
import com.educonnect.courseservice.service.CourseService;
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
}