package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.dto.AssignmentChangeResponse;
import com.educonnect.assignmentservice.dto.AssignmentUpdateRequest;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentChange;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.AssignmentChangeRepository;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.GroupSetRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class AssessmentRules {

    static final BigDecimal FULL_WEIGHT = BigDecimal.valueOf(100);

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentChangeRepository changeRepository;
    private final GroupSetRepository groupSetRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public AssessmentRules(AssignmentRepository assignmentRepository,
                           SubmissionRepository submissionRepository,
                           AssignmentChangeRepository changeRepository,
                           GroupSetRepository groupSetRepository) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.changeRepository = changeRepository;
        this.groupSetRepository = groupSetRepository;
    }

    public void requireWeightFits(UUID courseId, UUID assignmentId, BigDecimal weight) {
        if (weight == null || weight.signum() == 0) {
            return;
        }
        BigDecimal others = assignmentRepository.findByCourseId(courseId).stream()
                .filter(a -> !a.getId().equals(assignmentId))
                .map(Assignment::getWeight)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (others.add(weight).compareTo(FULL_WEIGHT) > 0) {
            throw new ConflictException("WEIGHT_EXCEEDED", "Dersteki değerlendirmelerin ağırlık toplamı %100'ü geçemez (kalan: %"
                    + FULL_WEIGHT.subtract(others).stripTrailingZeros().toPlainString() + ").");
        }
    }

    public static void requireLateWindow(LocalDateTime dueDate, LocalDateTime lateUntil) {
        if (lateUntil != null && dueDate != null && !lateUntil.isAfter(dueDate)) {
            throw new BadRequestException("INVALID_LATE_UNTIL", "Geç teslim bitişi son teslim tarihinden sonra olmalı.");
        }
    }

    public void requireGroupSet(UUID courseId, UUID groupSetId) {
        if (groupSetId != null && groupSetRepository.findById(groupSetId)
                .filter(set -> set.getCourseId().equals(courseId)).isEmpty()) {
            throw new BadRequestException("INVALID_GROUP_SET", "Grup seti bu derse ait değil.");
        }
    }

    public void requireValidGrade(Assignment assignment, BigDecimal grade) {
        if (grade != null && (grade.signum() < 0 || grade.compareTo(assignment.getMaxPoints()) > 0)) {
            throw new BadRequestException("INVALID_GRADE", "Puan 0 ile " + plain(assignment.getMaxPoints()) + " arasında olmalı.");
        }
    }

    public void requireDeletable(Assignment assignment) {
        if (!submissionRepository.findByAssignmentId(assignment.getId()).isEmpty()) {
            throw new ConflictException("ASSIGNMENT_HAS_SUBMISSIONS",
                    "Teslim alınmış değerlendirme silinemez; bilgilerini düzenleyebilirsiniz.");
        }
    }

    public Assignment update(Assignment assignment, AssignmentUpdateRequest request, UUID actorId) {
        List<AssignmentChange> changes = new ArrayList<>();
        Instant now = Instant.now(clock);
        if (request.title() != null && !request.title().strip().equals(assignment.getTitle())) {
            changes.add(change(assignment, "title", assignment.getTitle(), request.title().strip(), actorId, now));
            assignment.setTitle(request.title().strip());
        }
        if (request.description() != null && !request.description().equals(Objects.toString(assignment.getDescription(), ""))) {
            String description = request.description().isBlank() ? null : request.description();
            changes.add(change(assignment, "description", assignment.getDescription(), description, actorId, now));
            assignment.setDescription(description);
        }
        if (request.type() != null && request.type() != assignment.getType()) {
            changes.add(change(assignment, "type", assignment.getType().name(), request.type().name(), actorId, now));
            assignment.setType(request.type());
        }
        if (request.weight() != null && request.weight().compareTo(assignment.getWeight()) != 0) {
            requireWeightFits(assignment.getCourseId(), assignment.getId(), request.weight());
            changes.add(change(assignment, "weight", plain(assignment.getWeight()), plain(request.weight()), actorId, now));
            assignment.setWeight(request.weight());
        }
        if (request.maxPoints() != null && request.maxPoints().compareTo(assignment.getMaxPoints()) != 0) {
            requireGradesFit(assignment, request.maxPoints());
            changes.add(change(assignment, "maxPoints", plain(assignment.getMaxPoints()), plain(request.maxPoints()), actorId, now));
            assignment.setMaxPoints(request.maxPoints());
        }
        if (request.dueDate() != null && !request.dueDate().equals(assignment.getDueDate())) {
            changes.add(change(assignment, "dueDate", Objects.toString(assignment.getDueDate(), null),
                    request.dueDate().toString(), actorId, now));
            assignment.setDueDate(request.dueDate());
        }
        LocalDateTime lateUntil = Boolean.TRUE.equals(request.clearLateUntil()) ? null
                : request.lateUntil() != null ? request.lateUntil() : assignment.getLateUntil();
        if (!Objects.equals(lateUntil, assignment.getLateUntil())) {
            changes.add(change(assignment, "lateUntil", Objects.toString(assignment.getLateUntil(), null),
                    Objects.toString(lateUntil, null), actorId, now));
            assignment.setLateUntil(lateUntil);
        }
        if (request.latePenaltyPercent() != null && request.latePenaltyPercent().compareTo(assignment.getLatePenaltyPercent()) != 0) {
            changes.add(change(assignment, "latePenalty", plain(assignment.getLatePenaltyPercent()),
                    plain(request.latePenaltyPercent()), actorId, now));
            assignment.setLatePenaltyPercent(request.latePenaltyPercent());
        }
        UUID groupSetId = Boolean.TRUE.equals(request.clearGroupSet()) ? null
                : request.groupSetId() != null ? request.groupSetId() : assignment.getGroupSetId();
        if (!Objects.equals(groupSetId, assignment.getGroupSetId())) {
            if (!submissionRepository.findByAssignmentId(assignment.getId()).isEmpty()) {
                throw new ConflictException("ASSIGNMENT_HAS_SUBMISSIONS", "Teslim alınmış ödevin grup ayarı değiştirilemez.");
            }
            requireGroupSet(assignment.getCourseId(), groupSetId);
            changes.add(change(assignment, "groupSet", Objects.toString(assignment.getGroupSetId(), null),
                    Objects.toString(groupSetId, null), actorId, now));
            assignment.setGroupSetId(groupSetId);
        }
        requireLateWindow(assignment.getDueDate(), assignment.getLateUntil());
        Assignment saved = assignmentRepository.save(assignment);
        changeRepository.saveAll(changes);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AssignmentChangeResponse> changes(UUID assignmentId) {
        return changeRepository.findByAssignmentIdOrderByChangedAtDesc(assignmentId).stream()
                .map(c -> new AssignmentChangeResponse(c.getField(), c.getOldValue(), c.getNewValue(), c.getChangedBy(),
                        c.getChangedAt()))
                .toList();
    }

    private void requireGradesFit(Assignment assignment, BigDecimal maxPoints) {
        boolean exceeds = submissionRepository.findByAssignmentId(assignment.getId()).stream()
                .map(AssignmentSubmission::getGrade)
                .anyMatch(grade -> grade != null && grade.compareTo(maxPoints) > 0);
        if (exceeds) {
            throw new ConflictException("GRADES_EXCEED_MAX", "Verilmiş puanlardan biri yeni azami puandan yüksek.");
        }
    }

    private static AssignmentChange change(Assignment assignment, String field, String oldValue, String newValue,
                                           UUID actorId, Instant at) {
        return new AssignmentChange(assignment.getId(), field, oldValue, newValue, actorId, at);
    }

    static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
