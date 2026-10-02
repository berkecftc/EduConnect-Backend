package com.educonnect.postservice.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_title", columnList = "title"),
        @Index(name = "idx_post_status", columnList = "status"),
        @Index(name = "idx_post_author_id", columnList = "author_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status;

    @Column(name = "author_id")
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "publisher_type", nullable = false, length = 20)
    private PublisherType publisherType = PublisherType.STUDENT;

    @Column(name = "club_id")
    private UUID clubId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "publisher_name")
    private String publisherName;

    @Column(name = "comments_disabled", nullable = false)
    private boolean commentsDisabled;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Post() {}

    // Getter & Setter
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public PostCategory getCategory() { return category; }
    public void setCategory(PostCategory category) { this.category = category; }

    public PostStatus getStatus() { return status; }
    public void setStatus(PostStatus status) { this.status = status; }

    public UUID getAuthorId() { return authorId; }
    public void setAuthorId(UUID authorId) { this.authorId = authorId; }

    public PublisherType getPublisherType() { return publisherType; }
    public void setPublisherType(PublisherType publisherType) { this.publisherType = publisherType; }

    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }

    public boolean isCommentsDisabled() { return commentsDisabled; }
    public void setCommentsDisabled(boolean commentsDisabled) { this.commentsDisabled = commentsDisabled; }

    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }

    public void approve(UUID approver, Instant at) {
        this.approvedBy = approver;
        this.approvedAt = at;
        this.reviewNote = null;
    }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public boolean isOfficial() { return category != null && category.official(); }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
