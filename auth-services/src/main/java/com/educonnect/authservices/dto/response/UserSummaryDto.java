package com.educonnect.authservices.dto.response;

import java.util.Set;
import java.util.UUID;

public class UserSummaryDto {
    private UUID id;
    private String email;
    private Set<String> roles;
    private String status;
    private boolean emailVerified;

    public UserSummaryDto(UUID id, String email, Set<String> roles, String status, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.roles = roles;
        this.status = status;
        this.emailVerified = emailVerified;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
}
