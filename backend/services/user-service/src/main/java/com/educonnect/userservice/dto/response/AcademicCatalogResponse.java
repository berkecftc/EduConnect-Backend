package com.educonnect.userservice.dto.response;

import com.educonnect.userservice.models.ProgramLevel;

import java.util.List;
import java.util.UUID;

public record AcademicCatalogResponse(UUID id, String code, String name, boolean active, List<Department> departments) {

    public record Department(UUID id, String code, String name, boolean active, List<Program> programs) {
    }

    public record Program(UUID id, String code, String name, ProgramLevel level, int durationYears, boolean active) {
    }
}
