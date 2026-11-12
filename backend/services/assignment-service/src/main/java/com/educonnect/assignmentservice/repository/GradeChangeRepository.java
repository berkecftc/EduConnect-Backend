package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.GradeChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GradeChangeRepository extends JpaRepository<GradeChange, UUID> {
    List<GradeChange> findBySubmissionIdOrderByChangedAtDesc(UUID submissionId);
}
