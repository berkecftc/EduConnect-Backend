package com.educonnect.authservices.service;

import com.educonnect.authservices.dto.response.AuthResponse;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;

import java.util.Set;

final class AuthResponses {

    private AuthResponses() {
    }

    static AuthResponse of(String token, String refreshToken, String message, User user) {
        Set<Role> roles = user.getRoles() != null ? user.getRoles() : Set.of();
        return new AuthResponse(token, refreshToken, message, user.getId().toString(), user.getEmail(),
                RolePresentation.orderedRoles(roles), RolePresentation.primaryRole(roles),
                RolePresentation.pendingRequests(roles));
    }
}
