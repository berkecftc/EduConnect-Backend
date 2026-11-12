package com.educonnect.courseservice.controller;

import com.educonnect.courseservice.dto.CourseResponse;
import com.educonnect.courseservice.dto.CourseUpdateRequest;
import com.educonnect.courseservice.service.CourseLifecycleService;
import com.educonnect.courseservice.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CourseLifecycleController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final CourseLifecycleService lifecycleService;
    private final CourseService courseService;

    public CourseLifecycleController(CourseLifecycleService lifecycleService, CourseService courseService) {
        this.lifecycleService = lifecycleService;
        this.courseService = courseService;
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<CourseResponse> update(@PathVariable UUID courseId,
                                                 @Valid @RequestBody CourseUpdateRequest request,
                                                 @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        UUID userId = UUID.fromString(userIdHeader);
        lifecycleService.update(courseId, userId, request);
        return ResponseEntity.ok(courseService.getVisibleCourse(courseId, userId, false));
    }

    @PostMapping("/{courseId}/publish")
    public ResponseEntity<CourseResponse> publish(@PathVariable UUID courseId,
                                                  @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        UUID userId = UUID.fromString(userIdHeader);
        lifecycleService.publish(courseId, userId);
        return ResponseEntity.ok(courseService.getVisibleCourse(courseId, userId, false));
    }

    @PostMapping("/{courseId}/archive")
    public ResponseEntity<CourseResponse> archive(@PathVariable UUID courseId,
                                                  @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        UUID userId = UUID.fromString(userIdHeader);
        lifecycleService.archive(courseId, userId);
        return ResponseEntity.ok(courseService.getVisibleCourse(courseId, userId, false));
    }
}
