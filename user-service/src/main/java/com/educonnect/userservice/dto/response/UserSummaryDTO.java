package com.educonnect.userservice.dto.response;

import com.educonnect.userservice.models.Academician;

import java.util.UUID;

public record UserSummaryDTO(UUID id,
                             String firstName,
                             String lastName,
                             String title,
                             String academicTitle,
                             String department,
                             UUID departmentId) {

    public static UserSummaryDTO of(Academician academician) {
        return new UserSummaryDTO(academician.getId(), academician.getFirstName(), academician.getLastName(),
                academician.getTitle(),
                academician.getAcademicTitle() != null ? academician.getAcademicTitle().name() : null,
                academician.getDepartment(), academician.getDepartmentId());
    }
}
