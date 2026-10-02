package com.educonnect.authservices.repository;

import com.educonnect.authservices.models.AffiliationStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AffiliationStatusChangeRepository extends JpaRepository<AffiliationStatusChange, UUID> {

    List<AffiliationStatusChange> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
