package com.educonnect.userservice.repository;

import com.educonnect.userservice.models.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, UUID> {
    boolean existsByCodeIgnoreCase(String code);
}
