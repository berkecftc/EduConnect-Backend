package com.educonnect.userservice.dto.response;

import com.educonnect.userservice.models.AcademicTitle;
import com.educonnect.userservice.models.ProfileChangeRequest;

import java.time.Instant;
import java.util.UUID;

public record ProfileChangeResponse(UUID id,
                                    UUID userId,
                                    String currentName,
                                    String firstName,
                                    String lastName,
                                    AcademicTitle academicTitle,
                                    String titleLabel,
                                    UUID programId,
                                    UUID departmentId,
                                    String reason,
                                    ProfileChangeRequest.Status status,
                                    String reviewNote,
                                    Instant reviewedAt,
                                    Instant createdAt) {

    public static ProfileChangeResponse of(ProfileChangeRequest request, String currentName) {
        return new ProfileChangeResponse(request.getId(), request.getUserId(), currentName, request.getFirstName(),
                request.getLastName(), request.getAcademicTitle(),
                request.getAcademicTitle() == null ? null : request.getAcademicTitle().label(),
                request.getProgramId(), request.getDepartmentId(), request.getReason(), request.getStatus(),
                request.getReviewNote(), request.getReviewedAt(), request.getCreatedAt());
    }
}
