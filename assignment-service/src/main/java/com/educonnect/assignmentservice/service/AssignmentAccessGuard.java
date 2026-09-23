package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseClient;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class AssignmentAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(AssignmentAccessGuard.class);

    private final CourseClient courseClient;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    public AssignmentAccessGuard(CourseClient courseClient,
                                 AssignmentRepository assignmentRepository,
                                 SubmissionRepository submissionRepository) {
        this.courseClient = courseClient;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
    }

    public static UUID parseUserId(String userIdHeader) {
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulanamadı.");
        }
    }

    public static boolean isAdmin(String rolesHeader) {
        return rolesHeader != null && Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    public void requireInstructor(UUID courseId, UUID userId, String rolesHeader) {
        if (isAdmin(rolesHeader)) {
            return;
        }
        if (!Objects.equals(fetchInstructorId(courseId), userId)) {
            log.warn("Access denied: user {} is not the instructor of course {}", userId, courseId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem yalnızca dersin hocası tarafından yapılabilir.");
        }
    }

    public void requireEnrolledStudent(UUID courseId, UUID userId) {
        if (!isEnrolled(courseId, userId)) {
            log.warn("Access denied: user {} is not enrolled in course {}", userId, courseId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu derse kayıtlı değilsiniz.");
        }
    }

    public void requireCourseMember(UUID courseId, UUID userId, String rolesHeader) {
        if (isAdmin(rolesHeader)
                || Objects.equals(fetchInstructorId(courseId), userId)
                || isEnrolled(courseId, userId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu dersin ödevlerini görme yetkiniz yok.");
    }

    public void requireFileAccess(String normalizedFileUrl, UUID userId, String rolesHeader) {
        if (isAdmin(rolesHeader)) {
            return;
        }
        var assignment = assignmentRepository.findFirstByFileUrl(normalizedFileUrl);
        if (assignment.isPresent()) {
            requireCourseMember(assignment.get().getCourseId(), userId, rolesHeader);
            return;
        }
        var submission = submissionRepository.findFirstBySubmissionFileUrl(normalizedFileUrl);
        if (submission.isPresent()) {
            AssignmentSubmission s = submission.get();
            if (Objects.equals(s.getStudentId(), userId)) {
                return;
            }
            requireInstructor(getAssignment(s.getAssignmentId()).getCourseId(), userId, rolesHeader);
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı.");
    }

    public Assignment getAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödev bulunamadı."));
    }

    public AssignmentSubmission getSubmission(UUID submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teslim bulunamadı."));
    }

    private UUID fetchInstructorId(UUID courseId) {
        Map<String, Object> course;
        try {
            course = courseClient.getCourseById(courseId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ders bulunamadı.");
        }
        Object instructorId = course != null ? course.get("instructorId") : null;
        return instructorId != null ? UUID.fromString(instructorId.toString()) : null;
    }

    private boolean isEnrolled(UUID courseId, UUID userId) {
        try {
            List<UUID> studentIds = courseClient.getEnrolledStudentIds(courseId);
            return studentIds != null && studentIds.contains(userId);
        } catch (Exception e) {
            log.error("Could not fetch enrolled students for course {}: {}", courseId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ders bilgisi şu an doğrulanamıyor.");
        }
    }
}
