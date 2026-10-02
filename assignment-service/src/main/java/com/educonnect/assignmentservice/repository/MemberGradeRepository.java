package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.MemberGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberGradeRepository extends JpaRepository<MemberGrade, UUID> {
    Optional<MemberGrade> findBySubmissionIdAndStudentId(UUID submissionId, UUID studentId);
    List<MemberGrade> findBySubmissionIdIn(Collection<UUID> submissionIds);
}
