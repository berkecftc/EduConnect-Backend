package com.educonnect.assignmentservice.controller;

import com.educonnect.assignmentservice.dto.ExtensionRequest;
import com.educonnect.assignmentservice.dto.ExtensionResponse;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.service.AssignmentAccessGuard;
import com.educonnect.assignmentservice.service.ExtensionService;
import com.educonnect.common.web.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.educonnect.assignmentservice.service.AssignmentAccessGuard.parseUserId;

@RestController
@RequestMapping("/api/assignments/{assignmentId}/extensions")
public class ExtensionController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";

    private final ExtensionService extensionService;
    private final AssignmentAccessGuard accessGuard;

    public ExtensionController(ExtensionService extensionService, AssignmentAccessGuard accessGuard) {
        this.extensionService = extensionService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<List<ExtensionResponse>> list(@PathVariable UUID assignmentId,
                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                        @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        Assignment assignment = accessGuard.getAssignment(assignmentId);
        accessGuard.requireStaff(assignment.getCourseId(), parseUserId(userIdHeader), roles);
        return ResponseEntity.ok(extensionService.list(assignment));
    }

    @PutMapping("/{studentId}")
    public ResponseEntity<ExtensionResponse> grant(@PathVariable UUID assignmentId,
                                                   @PathVariable UUID studentId,
                                                   @Valid @RequestBody ExtensionRequest request,
                                                   @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                   @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        Assignment assignment = accessGuard.getAssignment(assignmentId);
        UUID userId = parseUserId(userIdHeader);
        accessGuard.requireAssignmentEditor(assignment.getCourseId(), userId, roles);
        if (!accessGuard.isEnrolled(assignment.getCourseId(), studentId)) {
            throw new NotFoundException("STUDENT_NOT_ENROLLED", "Öğrenci bu derse kayıtlı değil.");
        }
        return ResponseEntity.ok(extensionService.grant(assignment, studentId, request, userId));
    }

    @DeleteMapping("/{studentId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID assignmentId,
                                       @PathVariable UUID studentId,
                                       @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                       @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        Assignment assignment = accessGuard.getAssignment(assignmentId);
        accessGuard.requireAssignmentEditor(assignment.getCourseId(), parseUserId(userIdHeader), roles);
        extensionService.revoke(assignment, studentId);
        return ResponseEntity.noContent().build();
    }
}
