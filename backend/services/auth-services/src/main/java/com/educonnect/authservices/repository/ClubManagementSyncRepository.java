package com.educonnect.authservices.repository;

import com.educonnect.authservices.models.ClubManagementSync;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClubManagementSyncRepository extends JpaRepository<ClubManagementSync, UUID> {
}
