package com.educonnect.userservice.repository;

import com.educonnect.userservice.models.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByCodeIgnoreCase(String code);
}
