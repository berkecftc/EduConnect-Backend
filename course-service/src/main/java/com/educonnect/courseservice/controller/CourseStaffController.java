package com.educonnect.courseservice.controller;

import com.educonnect.common.web.ForbiddenException;
import com.educonnect.courseservice.dto.CoordinatorTransferRequest;
import com.educonnect.courseservice.dto.CourseStaffRequest;
import com.educonnect.courseservice.dto.CourseStaffResponse;
import com.educonnect.courseservice.dto.CourseStaffRoleRequest;
import com.educonnect.courseservice.service.CourseStaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}")
public class CourseStaffController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";

    private final CourseStaffService staffService;

    public CourseStaffController(CourseStaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/staff")
    public ResponseEntity<List<CourseStaffResponse>> list(@PathVariable UUID courseId,
                                                          @RequestHeader(value = USER_ID_HEADER, required = false) String userIdHeader,
                                                          @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        UUID viewerId = userIdHeader != null ? UUID.fromString(userIdHeader) : null;
        return ResponseEntity.ok(staffService.list(courseId, viewerId, isAdmin(roles)));
    }

    @PostMapping("/staff")
    public ResponseEntity<List<CourseStaffResponse>> add(@PathVariable UUID courseId,
                                                         @Valid @RequestBody CourseStaffRequest request,
                                                         @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.add(courseId, UUID.fromString(userIdHeader), request));
    }

    @PutMapping("/staff/{userId}")
    public ResponseEntity<List<CourseStaffResponse>> changeRole(@PathVariable UUID courseId,
                                                                @PathVariable UUID userId,
                                                                @Valid @RequestBody CourseStaffRoleRequest request,
                                                                @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(staffService.changeRole(courseId, UUID.fromString(userIdHeader), userId, request.role()));
    }

    @DeleteMapping("/staff/{userId}")
    public ResponseEntity<Void> remove(@PathVariable UUID courseId,
                                       @PathVariable UUID userId,
                                       @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                       @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        staffService.remove(courseId, UUID.fromString(userIdHeader), userId, isAdmin(roles));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/coordinator")
    public ResponseEntity<List<CourseStaffResponse>> transferCoordinator(@PathVariable UUID courseId,
                                                                         @Valid @RequestBody CoordinatorTransferRequest request,
                                                                         @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                                         @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        if (!isAdmin(roles)) {
            throw new ForbiddenException("Koordinatör devrini yalnızca yönetici yapabilir.");
        }
        return ResponseEntity.ok(staffService.transferCoordinator(courseId, UUID.fromString(userIdHeader), request));
    }

    private static boolean isAdmin(String rolesHeader) {
        return rolesHeader != null && Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
