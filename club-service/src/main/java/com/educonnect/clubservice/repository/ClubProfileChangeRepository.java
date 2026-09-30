package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubProfileChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClubProfileChangeRepository extends JpaRepository<ClubProfileChange, UUID> {
}
