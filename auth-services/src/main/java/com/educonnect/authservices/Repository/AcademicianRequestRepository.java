package com.educonnect.authservices.Repository;

import com.educonnect.authservices.models.AcademicianRegistrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcademicianRequestRepository extends JpaRepository<AcademicianRegistrationRequest, Long> {
    Optional<AcademicianRegistrationRequest> findByUserId(UUID userId);
}