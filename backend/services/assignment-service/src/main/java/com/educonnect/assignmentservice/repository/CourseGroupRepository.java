package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.CourseGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseGroupRepository extends JpaRepository<CourseGroup, UUID> {
    List<CourseGroup> findByGroupSetIdInOrderByNameAsc(Collection<UUID> groupSetIds);
    boolean existsByGroupSetIdAndNameIgnoreCase(UUID groupSetId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM CourseGroup g WHERE g.id = :id")
    Optional<CourseGroup> findByIdForUpdate(@Param("id") UUID id);
}
