package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.AssignmentChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentChangeRepository extends JpaRepository<AssignmentChange, UUID> {
    List<AssignmentChange> findByAssignmentIdOrderByChangedAtDesc(UUID assignmentId);
}
