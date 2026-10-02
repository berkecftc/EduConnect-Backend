package com.educonnect.userservice.repository;

import com.educonnect.userservice.models.ProfileChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, ProfileChangeRequest.Status status);

    List<ProfileChangeRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<ProfileChangeRequest> findByStatusOrderByCreatedAtAsc(ProfileChangeRequest.Status status);
}
