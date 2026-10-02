package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentExtension;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.model.GroupMember;
import com.educonnect.assignmentservice.model.MemberGrade;
import com.educonnect.assignmentservice.repository.AssignmentExtensionRepository;
import com.educonnect.assignmentservice.repository.GroupMemberRepository;
import com.educonnect.assignmentservice.repository.MemberGradeRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class GroupWork {

    private final GroupMemberRepository memberRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentExtensionRepository extensionRepository;
    private final MemberGradeRepository memberGradeRepository;

    public GroupWork(GroupMemberRepository memberRepository,
                     SubmissionRepository submissionRepository,
                     AssignmentExtensionRepository extensionRepository,
                     MemberGradeRepository memberGradeRepository) {
        this.memberRepository = memberRepository;
        this.submissionRepository = submissionRepository;
        this.extensionRepository = extensionRepository;
        this.memberGradeRepository = memberGradeRepository;
    }

    public Optional<GroupMember> membership(Assignment assignment, UUID studentId) {
        if (!assignment.isGroupWork()) {
            return Optional.empty();
        }
        return memberRepository.findByGroupSetIdAndStudentId(assignment.getGroupSetId(), studentId);
    }

    public Optional<AssignmentSubmission> submissionOf(Assignment assignment, UUID studentId) {
        if (!assignment.isGroupWork()) {
            return submissionRepository.findByAssignmentIdAndStudentIdAndGroupIdIsNull(assignment.getId(), studentId);
        }
        return membership(assignment, studentId)
                .flatMap(member -> submissionRepository.findByAssignmentIdAndGroupId(assignment.getId(), member.getGroupId()));
    }

    public DeadlinePolicy.Window window(Assignment assignment, UUID studentId) {
        if (!assignment.isGroupWork()) {
            return DeadlinePolicy.window(assignment,
                    extensionRepository.findByAssignmentIdAndStudentId(assignment.getId(), studentId).orElse(null));
        }
        return membership(assignment, studentId)
                .map(member -> groupWindow(assignment, member.getGroupId()))
                .orElseGet(() -> DeadlinePolicy.window(assignment, null));
    }

    public DeadlinePolicy.Window groupWindow(Assignment assignment, UUID groupId) {
        List<UUID> members = memberRepository.findByGroupId(groupId).stream().map(GroupMember::getStudentId).toList();
        AssignmentExtension latest = members.isEmpty() ? null
                : extensionRepository.findByAssignmentIdAndStudentIdIn(assignment.getId(), members).stream()
                .max(Comparator.comparing(AssignmentExtension::getDueDate))
                .orElse(null);
        return DeadlinePolicy.window(assignment, latest);
    }

    public boolean canSee(AssignmentSubmission submission, UUID userId) {
        if (Objects.equals(submission.getStudentId(), userId)) {
            return true;
        }
        return submission.getGroupId() != null && memberRepository.findByGroupId(submission.getGroupId()).stream()
                .anyMatch(member -> member.getStudentId().equals(userId));
    }

    public BigDecimal gradeOf(AssignmentSubmission submission, UUID studentId) {
        if (submission.getGroupId() == null) {
            return submission.getGrade();
        }
        return memberGradeRepository.findBySubmissionIdAndStudentId(submission.getId(), studentId)
                .map(MemberGrade::getGrade)
                .orElse(submission.getGrade());
    }

    public Map<UUID, List<UUID>> membersByGroup(Collection<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findByGroupIdIn(groupIds).stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId,
                        Collectors.mapping(GroupMember::getStudentId, Collectors.toList())));
    }

    public Map<UUID, Map<UUID, BigDecimal>> overrides(Collection<UUID> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        return memberGradeRepository.findBySubmissionIdIn(submissionIds).stream()
                .collect(Collectors.groupingBy(MemberGrade::getSubmissionId,
                        Collectors.toMap(MemberGrade::getStudentId, MemberGrade::getGrade)));
    }
}
