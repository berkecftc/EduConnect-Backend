package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    List<GroupMember> findByGroupSetIdIn(Collection<UUID> groupSetIds);
    List<GroupMember> findByGroupId(UUID groupId);
    Optional<GroupMember> findByGroupSetIdAndStudentId(UUID groupSetId, UUID studentId);
    long countByGroupId(UUID groupId);
}
