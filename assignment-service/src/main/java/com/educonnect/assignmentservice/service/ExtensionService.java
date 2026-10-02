package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.UserClient;
import com.educonnect.assignmentservice.dto.ExtensionRequest;
import com.educonnect.assignmentservice.dto.ExtensionResponse;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentExtension;
import com.educonnect.assignmentservice.repository.AssignmentExtensionRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ExtensionService {

    private final AssignmentExtensionRepository extensionRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentDirectory studentDirectory;
    private final GroupWork groupWork;
    private final Clock clock = Clock.systemDefaultZone();

    public ExtensionService(AssignmentExtensionRepository extensionRepository,
                            SubmissionRepository submissionRepository,
                            GroupWork groupWork,
                            StudentDirectory studentDirectory) {
        this.extensionRepository = extensionRepository;
        this.submissionRepository = submissionRepository;
        this.studentDirectory = studentDirectory;
        this.groupWork = groupWork;
    }

    @Transactional(readOnly = true)
    public List<ExtensionResponse> list(Assignment assignment) {
        List<AssignmentExtension> extensions = extensionRepository.findByAssignmentIdOrderByDueDateAsc(assignment.getId());
        Map<UUID, UserClient.UserProfileDTO> students = studentDirectory.byId(
                extensions.stream().map(AssignmentExtension::getStudentId).toList());
        return extensions.stream().map(e -> response(assignment, e, students.get(e.getStudentId()))).toList();
    }

    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, key = "#studentId")
    public ExtensionResponse grant(Assignment assignment, UUID studentId, ExtensionRequest request, UUID actorId) {
        if (assignment.getDueDate() != null && !request.dueDate().isAfter(assignment.getDueDate())) {
            throw new BadRequestException("EXTENSION_NOT_LATER", "Uzatılan tarih son teslim tarihinden sonra olmalı.");
        }
        AssignmentExtension extension = extensionRepository.findByAssignmentIdAndStudentId(assignment.getId(), studentId)
                .orElseGet(() -> new AssignmentExtension(assignment.getId(), studentId));
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        extension.grant(request.dueDate(), reason, actorId, Instant.now(clock));
        AssignmentExtension saved = extensionRepository.save(extension);
        refreshLateFlag(assignment, studentId);
        return response(assignment, saved, studentDirectory.byId(List.of(studentId)).get(studentId));
    }

    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, key = "#studentId")
    public void revoke(Assignment assignment, UUID studentId) {
        AssignmentExtension extension = extensionRepository.findByAssignmentIdAndStudentId(assignment.getId(), studentId)
                .orElseThrow(() -> new NotFoundException("EXTENSION_NOT_FOUND", "Bu öğrenci için süre uzatımı yok."));
        extensionRepository.delete(extension);
        refreshLateFlag(assignment, studentId);
    }

    private void refreshLateFlag(Assignment assignment, UUID studentId) {
        groupWork.submissionOf(assignment, studentId).ifPresent(submission -> {
            DeadlinePolicy.Window window = submission.getGroupId() != null
                    ? groupWork.groupWindow(assignment, submission.getGroupId())
                    : groupWork.window(assignment, studentId);
            boolean late = window.isLate(submission.getSubmittedAt());
            if (late != submission.isLate()) {
                submission.setLate(late);
                submissionRepository.save(submission);
            }
        });
    }

    private static ExtensionResponse response(Assignment assignment, AssignmentExtension extension,
                                              UserClient.UserProfileDTO student) {
        DeadlinePolicy.Window window = DeadlinePolicy.window(assignment, extension);
        return new ExtensionResponse(extension.getStudentId(),
                student != null ? student.getFirstName() + " " + student.getLastName() : "Bilinmeyen Öğrenci",
                student != null ? student.getStudentNumber() : null,
                window.due(), window.lateUntil(), extension.getReason(), extension.getGrantedBy(), extension.getGrantedAt());
    }
}
