package com.educonnect.userservice.repository;

import com.educonnect.userservice.models.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FacultyRepository extends JpaRepository<Faculty, UUID> {
    boolean existsByCodeIgnoreCase(String code);
}
