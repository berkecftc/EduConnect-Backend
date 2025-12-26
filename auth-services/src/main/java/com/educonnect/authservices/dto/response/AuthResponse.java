package com.educonnect.authservices.dto.response;

import java.util.Set;

public class AuthResponse {
    private String token;
    private String refreshToken;
    private String message;
    private String userId;
    private String username;
    private Set<String> roles;

    public AuthResponse(String token, String refreshToken, String message, String userId, String username, Set<String> roles) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.message = message;
        this.userId = userId;
        this.username = username;
        this.roles = roles;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}