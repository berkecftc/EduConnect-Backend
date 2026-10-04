package com.educonnect.postservice.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.Instant;
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

    @Column(name = "moderation_flag")
    private String moderationFlag;

    @Column(name = "course_label")
    private String courseLabel;

    @Column(name = "attachment_url", length = 1000)
    private String attachmentUrl;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "declaration_accepted_at")
    private Instant declarationAcceptedAt;

    @Column(name = "accepted_comment_id")
    private UUID acceptedCommentId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

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

    public String getModerationFlag() { return moderationFlag; }
    public void setModerationFlag(String moderationFlag) { this.moderationFlag = moderationFlag; }

    public String getCourseLabel() { return courseLabel; }
    public void setCourseLabel(String courseLabel) { this.courseLabel = courseLabel; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public String getAttachmentName() { return attachmentName; }

    public void attach(String url, String name) {
        this.attachmentUrl = url;
        this.attachmentName = name;
    }

    public Instant getDeclarationAcceptedAt() { return declarationAcceptedAt; }
    public void setDeclarationAcceptedAt(Instant declarationAcceptedAt) { this.declarationAcceptedAt = declarationAcceptedAt; }

    public UUID getAcceptedCommentId() { return acceptedCommentId; }
    public void setAcceptedCommentId(UUID acceptedCommentId) { this.acceptedCommentId = acceptedCommentId; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public boolean isOfficial() { return category != null && category.official(); }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
