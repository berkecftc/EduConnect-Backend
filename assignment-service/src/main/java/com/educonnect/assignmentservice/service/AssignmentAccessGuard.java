package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseAccess;
import com.educonnect.assignmentservice.client.CourseInternalClient;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.assignmentservice.repository.SubmissionVersionRepository;
import com.educonnect.common.web.ConflictException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;

@Component
public class AssignmentAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(AssignmentAccessGuard.class);

    private static final Set<String> EDITABLE = Set.of("DRAFT", "OPEN", "ACTIVE");
    private static final Set<String> GRADABLE = Set.of("OPEN", "ACTIVE", "COMPLETED");
    private static final Set<String> RUNNING = Set.of("OPEN", "ACTIVE");

    private final CourseInternalClient courseInternalClient;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;

    public AssignmentAccessGuard(CourseInternalClient courseInternalClient,
                                 AssignmentRepository assignmentRepository,
                                 SubmissionRepository submissionRepository,
                                 SubmissionVersionRepository versionRepository) {
        this.courseInternalClient = courseInternalClient;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
    }

    public static UUID parseUserId(String userIdHeader) {
        if (userIdHeader == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulanamadı.");
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulanamadı.");
        }
    }

    public static boolean isAdmin(String rolesHeader) {
        return rolesHeader != null && Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    public CourseAccess requireStaff(UUID courseId, UUID userId, String rolesHeader) {
        CourseAccess access = accessOf(courseId, userId);
        if (!isAdmin(rolesHeader) && !access.staff()) {
            log.warn("Access denied: user {} is not on the staff of course {}", userId, courseId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem yalnızca dersin kadrosu tarafından yapılabilir.");
        }
        return access;
    }

    public void requireAssignmentEditor(UUID courseId, UUID userId, String rolesHeader) {
        CourseAccess access = accessOf(courseId, userId);
        if (!isAdmin(rolesHeader) && !access.teaches()) {
            log.warn("Access denied: user {} does not teach course {}", userId, courseId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ödevleri yalnızca dersin koordinatörü veya hocası yönetebilir.");
        }
        if (!EDITABLE.contains(access.status())) {
            throw new ConflictException("COURSE_READ_ONLY", "Tamamlanmış veya arşivlenmiş derste ödev eklenemez ya da silinemez.");
        }
    }

    public void requireGrader(UUID courseId, UUID userId, String rolesHeader) {
        CourseAccess access = requireStaff(courseId, userId, rolesHeader);
        if (!GRADABLE.contains(access.status())) {
            throw new ConflictException("COURSE_READ_ONLY", "Arşivlenmiş derste puan değiştirilemez.");
        }
    }

    public void requireGradePublisher(UUID courseId, UUID userId, String rolesHeader) {
        CourseAccess access = accessOf(courseId, userId);
        if (!isAdmin(rolesHeader) && !access.teaches()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Puanları yalnızca dersin koordinatörü veya hocası ilan edebilir.");
        }
        if (!GRADABLE.contains(access.status())) {
            throw new ConflictException("COURSE_READ_ONLY", "Arşivlenmiş derste puan ilan edilemez.");
        }
    }

    public CourseAccess requireEnrolled(UUID courseId, UUID userId) {
        CourseAccess access = accessOf(courseId, userId);
        if (!access.enrolled()) {
            log.warn("Access denied: user {} is not enrolled in course {}", userId, courseId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu derse kayıtlı değilsiniz.");
        }
        return access;
    }

    public void requireEnrolledStudent(UUID courseId, UUID userId) {
        CourseAccess access = requireEnrolled(courseId, userId);
        if (!RUNNING.contains(access.status())) {
            throw new ConflictException("COURSE_CLOSED", "Ders tamamlandığı için teslim yapılamaz.");
        }
    }

    public void requireCourseMember(UUID courseId, UUID userId, String rolesHeader) {
        if (isAdmin(rolesHeader)) {
            return;
        }
        CourseAccess access = accessOf(courseId, userId);
        if (!access.staff() && !access.enrolled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu dersin ödevlerini görme yetkiniz yok.");
        }
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
        var submission = submissionRepository.findFirstBySubmissionFileUrl(normalizedFileUrl)
                .or(() -> versionRepository.findFirstByFileUrl(normalizedFileUrl)
                        .flatMap(version -> submissionRepository.findById(version.getSubmissionId())));
        if (submission.isPresent()) {
            requireSubmissionViewer(submission.get(), userId, rolesHeader);
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dosya bulunamadı.");
    }

    public void requireSubmissionViewer(AssignmentSubmission submission, UUID userId, String rolesHeader) {
        if (isAdmin(rolesHeader) || Objects.equals(submission.getStudentId(), userId)) {
            return;
        }
        requireStaff(getAssignment(submission.getAssignmentId()).getCourseId(), userId, rolesHeader);
    }

    public boolean isEnrolled(UUID courseId, UUID userId) {
        return accessOf(courseId, userId).enrolled();
    }

    public Assignment getAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödev bulunamadı."));
    }

    public AssignmentSubmission getSubmission(UUID submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teslim bulunamadı."));
    }

    private CourseAccess accessOf(UUID courseId, UUID userId) {
        try {
            return courseInternalClient.access(courseId, userId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ders bulunamadı.");
        } catch (FeignException e) {
            log.error("Could not fetch course access for course {}: {}", courseId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ders bilgisi şu an doğrulanamıyor.");
        }
    }
}
