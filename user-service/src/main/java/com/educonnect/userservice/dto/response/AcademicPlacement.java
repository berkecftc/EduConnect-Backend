package com.educonnect.userservice.dto.response;

import com.educonnect.userservice.models.ProgramLevel;

import java.util.UUID;

public record AcademicPlacement(UUID facultyId, String facultyName, UUID departmentId, String departmentName,
                                UUID programId, String programName, ProgramLevel programLevel, Integer durationYears,
                                boolean active) {
}
