package com.educonnect.postservice.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Post beğeni entity'si.
 * Bir kullanıcı aynı post'u en fazla bir kez beğenebilir (unique constraint: post_id + user_id).
 */
@Entity
@Table(name = "post_likes", schema = "post_db",
        uniqueConstraints = @UniqueConstraint(name = "uq_post_like_user", columnNames = {"post_id", "user_id"}),
        indexes = {
                @Index(name = "idx_like_post_id", columnList = "post_id"),
                @Index(name = "idx_like_user_id", columnList = "user_id")
        })
@EntityListeners(AuditingEntityListener.class)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostLike() {}

    // Getter & Setter
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

