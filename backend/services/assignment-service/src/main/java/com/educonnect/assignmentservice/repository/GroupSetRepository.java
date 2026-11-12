package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.GroupSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GroupSetRepository extends JpaRepository<GroupSet, UUID> {
    List<GroupSet> findByCourseIdOrderByNameAsc(UUID courseId);
    boolean existsByCourseIdAndNameIgnoreCase(UUID courseId, String name);
    void deleteByCourseId(UUID courseId);
}
