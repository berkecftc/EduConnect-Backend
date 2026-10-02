package com.educonnect.courseservice.controller;

import com.educonnect.courseservice.dto.CourseAccessResponse;
import com.educonnect.courseservice.service.CourseService;
import com.educonnect.courseservice.service.StudentRelationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/internal")
public class InternalCourseController {

    private final CourseService courseService;
    private final StudentRelationService studentRelationService;

    public InternalCourseController(CourseService courseService, StudentRelationService studentRelationService) {
        this.courseService = courseService;
        this.studentRelationService = studentRelationService;
    }

    @GetMapping("/{courseId}/enrolled-students/ids")
    public ResponseEntity<List<UUID>> getEnrolledStudentIds(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.getEnrolledStudentIds(courseId));
    }

    @GetMapping("/{courseId}/access/{userId}")
    public ResponseEntity<CourseAccessResponse> access(@PathVariable UUID courseId, @PathVariable UUID userId) {
        return ResponseEntity.ok(courseService.accessOf(courseId, userId));
    }

    @GetMapping("/instructors/{instructorId}/course-ids")
    public ResponseEntity<List<UUID>> getInstructorCourseIds(@PathVariable UUID instructorId) {
        return ResponseEntity.ok(courseService.getInstructorCourseIds(instructorId));
    }

    @GetMapping("/students/{studentId}/course-ids")
    public ResponseEntity<List<UUID>> getActiveCourseIds(@PathVariable UUID studentId) {
        return ResponseEntity.ok(courseService.getActiveCourseIds(studentId));
    }

    @GetMapping("/staff/{viewerId}/students/{studentId}")
    public ResponseEntity<Map<String, Boolean>> teachesStudent(@PathVariable UUID viewerId, @PathVariable UUID studentId) {
        return ResponseEntity.ok(Map.of("related", studentRelationService.teachesStudent(viewerId, studentId)));
    }
}
