package com.educonnect.courseservice.controller;

import com.educonnect.courseservice.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/internal")
public class InternalCourseController {

    private final CourseService courseService;

    public InternalCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/{courseId}/enrolled-students/ids")
    public ResponseEntity<List<UUID>> getEnrolledStudentIds(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.getEnrolledStudentIds(courseId));
    }
}
