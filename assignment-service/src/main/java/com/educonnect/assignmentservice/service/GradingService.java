package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.dto.AssignmentResponse;
import com.educonnect.assignmentservice.dto.GradeChangeResponse;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.model.GradeChange;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.GradeChangeRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final GradeChangeRepository changeRepository;
    private final AssessmentRules assessmentRules;
    private final AssignmentService assignmentService;
    private final Clock clock = Clock.systemDefaultZone();

    public GradingService(AssignmentRepository assignmentRepository,
                          SubmissionRepository submissionRepository,
                          GradeChangeRepository changeRepository,
                          AssessmentRules assessmentRules,
                          AssignmentService assignmentService) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.changeRepository = changeRepository;
        this.assessmentRules = assessmentRules;
        this.assignmentService = assignmentService;
    }

    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, key = "#submission.studentId")
    public void grade(AssignmentSubmission submission, Assignment assignment, BigDecimal grade, String feedback,
                      String reason, UUID actorId) {
        assessmentRules.requireValidGrade(assignment, grade);
        BigDecimal previous = submission.getGrade();
        boolean gradeChanged = !sameGrade(previous, grade);
        boolean feedbackChanged = !Objects.equals(blankToNull(submission.getFeedback()), blankToNull(feedback));
        if (!gradeChanged && !feedbackChanged) {
            return;
        }
        boolean afterPublication = assignment.gradesPublished();
        String normalizedReason = blankToNull(reason);
        if (afterPublication && previous != null && gradeChanged && normalizedReason == null) {
            throw new BadRequestException("GRADE_CHANGE_REASON_REQUIRED",
                    "İlan edilmiş bir puanı değiştirmek için gerekçe yazılmalı.");
        }
        submission.setGrade(grade);
        submission.setFeedback(blankToNull(feedback));
        submissionRepository.save(submission);
        changeRepository.save(new GradeChange(submission.getId(), previous, grade, feedbackChanged, afterPublication,
                normalizedReason, actorId, Instant.now(clock)));
        if (afterPublication && previous != null && gradeChanged) {
            log.info("Published grade changed: submission={}, by={}", submission.getId(), actorId);
        }
    }

    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, allEntries = true)
    public AssignmentResponse publish(Assignment assignment, UUID actorId) {
        if (assignment.gradesPublished()) {
            throw new ConflictException("GRADES_ALREADY_PUBLISHED", "Bu değerlendirmenin puanları zaten ilan edildi.");
        }
        assignment.publishGrades(actorId, Instant.now(clock));
        Assignment saved = assignmentRepository.save(assignment);
        log.info("Grades published: assignment={}, by={}", assignment.getId(), actorId);
        return assignmentService.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GradeChangeResponse> history(UUID submissionId) {
        if (!submissionRepository.existsById(submissionId)) {
            throw new NotFoundException("SUBMISSION_NOT_FOUND", "Teslim bulunamadı");
        }
        return changeRepository.findBySubmissionIdOrderByChangedAtDesc(submissionId).stream()
                .map(c -> new GradeChangeResponse(c.getOldGrade(), c.getNewGrade(), c.isFeedbackChanged(),
                        c.isAfterPublication(), c.getReason(), c.getChangedBy(), c.getChangedAt()))
                .toList();
    }

    private static boolean sameGrade(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
