package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.AssignmentExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentExtensionRepository extends JpaRepository<AssignmentExtension, UUID> {
    Optional<AssignmentExtension> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    List<AssignmentExtension> findByAssignmentIdOrderByDueDateAsc(UUID assignmentId);
    List<AssignmentExtension> findByStudentId(UUID studentId);
}
