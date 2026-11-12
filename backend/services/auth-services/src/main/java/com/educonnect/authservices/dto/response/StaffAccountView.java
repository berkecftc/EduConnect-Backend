package com.educonnect.authservices.dto.response;

import com.educonnect.authservices.models.StaffGrant;
import com.educonnect.authservices.models.StaffPermission;
import com.educonnect.authservices.models.User;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StaffAccountView(UUID id, String email, String displayName, String status, List<Grant> grants) {

    public record Grant(StaffPermission permission, UUID facultyId) {
    }

    public static StaffAccountView of(User user) {
        return new StaffAccountView(user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus().name(),
                user.getStaffGrants().stream()
                        .sorted(Comparator.comparing(StaffGrant::getPermission)
                                .thenComparing(grant -> Objects.toString(grant.getFacultyId(), "")))
                        .map(grant -> new Grant(grant.getPermission(), grant.getFacultyId()))
                        .toList());
    }
}
