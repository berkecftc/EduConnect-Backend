package com.educonnect.authservices.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class StaffGrant implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 30)
    private StaffPermission permission;

    @Column(name = "faculty_id")
    private UUID facultyId;

    protected StaffGrant() {
    }

    public StaffGrant(StaffPermission permission, UUID facultyId) {
        this.permission = permission;
        this.facultyId = facultyId;
    }

    public StaffPermission getPermission() {
        return permission;
    }

    public UUID getFacultyId() {
        return facultyId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StaffGrant grant && permission == grant.permission && Objects.equals(facultyId, grant.facultyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(permission, facultyId);
    }
}
