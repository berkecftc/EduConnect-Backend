package com.educonnect.assignmentservice.controller;

import com.educonnect.assignmentservice.dto.GroupRequest;
import com.educonnect.assignmentservice.dto.GroupSetRequest;
import com.educonnect.assignmentservice.dto.GroupSetResponse;
import com.educonnect.assignmentservice.model.CourseGroup;
import com.educonnect.assignmentservice.model.GroupSet;
import com.educonnect.assignmentservice.service.AssignmentAccessGuard;
import com.educonnect.assignmentservice.service.GroupService;
import com.educonnect.common.web.NotFoundException;
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

import java.util.List;
import java.util.UUID;

import static com.educonnect.assignmentservice.service.AssignmentAccessGuard.parseUserId;

@RestController
@RequestMapping("/api/assignments")
public class GroupController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";

    private final GroupService groupService;
    private final AssignmentAccessGuard accessGuard;

    public GroupController(GroupService groupService, AssignmentAccessGuard accessGuard) {
        this.groupService = groupService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/course/{courseId}/group-sets")
    public ResponseEntity<List<GroupSetResponse>> list(@PathVariable UUID courseId,
                                                       @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                       @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        UUID userId = parseUserId(userIdHeader);
        boolean staff = accessGuard.requireCourseMember(courseId, userId, roles);
        return ResponseEntity.ok(groupService.list(courseId, userId, staff));
    }

    @PostMapping("/course/{courseId}/group-sets")
    public ResponseEntity<List<GroupSetResponse>> createSet(@PathVariable UUID courseId,
                                                            @Valid @RequestBody GroupSetRequest request,
                                                            @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        UUID userId = parseUserId(userIdHeader);
        accessGuard.requireAssignmentEditor(courseId, userId, roles);
        groupService.createSet(courseId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.list(courseId, userId, true));
    }

    @PutMapping("/group-sets/{setId}")
    public ResponseEntity<List<GroupSetResponse>> updateSet(@PathVariable UUID setId,
                                                            @Valid @RequestBody GroupSetRequest request,
                                                            @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        GroupSet set = groupService.set(setId);
        UUID userId = parseUserId(userIdHeader);
        accessGuard.requireAssignmentEditor(set.getCourseId(), userId, roles);
        groupService.updateSet(set, request);
        return ResponseEntity.ok(groupService.list(set.getCourseId(), userId, true));
    }

    @DeleteMapping("/group-sets/{setId}")
    public ResponseEntity<Void> deleteSet(@PathVariable UUID setId,
                                          @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                          @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        GroupSet set = groupService.set(setId);
        accessGuard.requireAssignmentEditor(set.getCourseId(), parseUserId(userIdHeader), roles);
        groupService.deleteSet(set);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/group-sets/{setId}/groups")
    public ResponseEntity<List<GroupSetResponse>> createGroup(@PathVariable UUID setId,
                                                              @Valid @RequestBody GroupRequest request,
                                                              @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                              @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        GroupSet set = groupService.set(setId);
        UUID userId = parseUserId(userIdHeader);
        accessGuard.requireAssignmentEditor(set.getCourseId(), userId, roles);
        groupService.createGroup(set, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.list(set.getCourseId(), userId, true));
    }

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID groupId,
                                            @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CourseGroup group = groupService.group(groupId);
        accessGuard.requireAssignmentEditor(courseOf(group), parseUserId(userIdHeader), roles);
        groupService.deleteGroup(group);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/groups/{groupId}/members/{studentId}")
    public ResponseEntity<List<GroupSetResponse>> addMember(@PathVariable UUID groupId,
                                                            @PathVariable UUID studentId,
                                                            @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CourseGroup group = groupService.group(groupId);
        UUID courseId = courseOf(group);
        UUID userId = parseUserId(userIdHeader);
        accessGuard.requireAssignmentEditor(courseId, userId, roles);
        if (!accessGuard.isEnrolled(courseId, studentId)) {
            throw new NotFoundException("STUDENT_NOT_ENROLLED", "Öğrenci bu derse kayıtlı değil.");
        }
        groupService.addMember(group, studentId, userId);
        return ResponseEntity.ok(groupService.list(courseId, userId, true));
    }

    @DeleteMapping("/groups/{groupId}/members/{studentId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID groupId,
                                             @PathVariable UUID studentId,
                                             @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                             @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CourseGroup group = groupService.group(groupId);
        accessGuard.requireAssignmentEditor(courseOf(group), parseUserId(userIdHeader), roles);
        groupService.removeMember(group, studentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{groupId}/join")
    public ResponseEntity<List<GroupSetResponse>> join(@PathVariable UUID groupId,
                                                       @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        CourseGroup group = groupService.group(groupId);
        UUID courseId = courseOf(group);
        UUID studentId = parseUserId(userIdHeader);
        accessGuard.requireEnrolledStudent(courseId, studentId);
        groupService.join(group, studentId);
        return ResponseEntity.ok(groupService.list(courseId, studentId, false));
    }

    @PostMapping("/groups/{groupId}/leave")
    public ResponseEntity<List<GroupSetResponse>> leave(@PathVariable UUID groupId,
                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        CourseGroup group = groupService.group(groupId);
        UUID courseId = courseOf(group);
        UUID studentId = parseUserId(userIdHeader);
        accessGuard.requireEnrolledStudent(courseId, studentId);
        groupService.leave(group, studentId);
        return ResponseEntity.ok(groupService.list(courseId, studentId, false));
    }

    private UUID courseOf(CourseGroup group) {
        return groupService.set(group.getGroupSetId()).getCourseId();
    }
}
