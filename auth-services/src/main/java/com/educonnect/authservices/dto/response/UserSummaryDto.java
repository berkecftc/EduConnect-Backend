package com.educonnect.authservices.dto.response;

import java.util.Set;
import java.util.UUID;

public class UserSummaryDto {
    private UUID id;
    private String email;
    private Set<String> roles;

    public UserSummaryDto(UUID id, String email, Set<String> roles) {
        this.id = id;
        this.email = email;
        this.roles = roles;
    }

    // Getter & Setter
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
}