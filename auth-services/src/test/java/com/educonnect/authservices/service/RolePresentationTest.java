package com.educonnect.authservices.service;

import com.educonnect.authservices.models.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RolePresentationTest {

    @Test
    void pendingRolesGoLastSoStudentStaysPrimary() {
        Set<Role> roles = Set.of(Role.ROLE_STUDENT, Role.ROLE_PENDING_CLUB_OFFICIAL);

        assertThat(RolePresentation.orderedRoles(roles)).containsExactly("ROLE_STUDENT", "ROLE_PENDING_CLUB_OFFICIAL");
        assertThat(RolePresentation.primaryRole(roles)).isEqualTo("ROLE_STUDENT");
        assertThat(RolePresentation.pendingRequests(roles)).containsExactly("CLUB_OFFICIAL");
    }

    @Test
    void keepsPreviousOrderingForActiveRoles() {
        assertThat(RolePresentation.orderedRoles(Set.of(Role.ROLE_STUDENT, Role.ROLE_CLUB_OFFICIAL)))
                .containsExactly("ROLE_CLUB_OFFICIAL", "ROLE_STUDENT");
        assertThat(RolePresentation.orderedRoles(Set.of(Role.ROLE_STUDENT, Role.ROLE_ADMIN)))
                .first().isEqualTo("ROLE_ADMIN");
        assertThat(RolePresentation.primaryRole(Set.of(Role.ROLE_ACADEMICIAN, Role.ROLE_CLUB_OFFICIAL)))
                .isEqualTo("ROLE_ACADEMICIAN");
    }

    @Test
    void pendingOnlyUserHasNoPrimaryRole() {
        assertThat(RolePresentation.primaryRole(Set.of(Role.ROLE_PENDING_ACADEMICIAN))).isNull();
        assertThat(RolePresentation.pendingRequests(Set.of(Role.ROLE_PENDING_ACADEMICIAN))).containsExactly("ACADEMICIAN");
    }
}
