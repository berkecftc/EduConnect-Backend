package com.educonnect.authservices.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_change_tokens")
public class EmailChangeToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "new_email", nullable = false)
    private String newEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailChangeToken() {
    }

    public EmailChangeToken(String tokenHash, UUID userId, String newEmail, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.newEmail = newEmail;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
