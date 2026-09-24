package com.educonnect.authservices.service;

import com.educonnect.authservices.models.Role;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class RolePresentation {

    private static final String PENDING_PREFIX = "ROLE_PENDING_";
    private static final List<Role> PRIMARY_PRIORITY = List.of(
            Role.ROLE_ADMIN, Role.ROLE_ACADEMICIAN, Role.ROLE_CLUB_OFFICIAL, Role.ROLE_STUDENT);

    private RolePresentation() {
    }

    public static Set<String> orderedRoles(Set<Role> roles) {
        return roles.stream()
                .sorted(Comparator.comparingInt(RolePresentation::rank).thenComparing(Role::name))
                .map(Role::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String primaryRole(Set<Role> roles) {
        return PRIMARY_PRIORITY.stream()
                .filter(roles::contains)
                .map(Role::name)
                .findFirst()
                .orElse(null);
    }

    public static List<String> pendingRequests(Set<Role> roles) {
        return roles.stream()
                .map(Role::name)
                .filter(name -> name.startsWith(PENDING_PREFIX))
                .map(name -> name.substring(PENDING_PREFIX.length()))
                .sorted()
                .toList();
    }

    private static int rank(Role role) {
        if (role.name().startsWith(PENDING_PREFIX)) {
            return Integer.MAX_VALUE;
        }
        int index = PRIMARY_PRIORITY.indexOf(role);
        return index >= 0 ? index : PRIMARY_PRIORITY.size();
    }
}
