package com.educonnect.courseservice.model;

import com.educonnect.common.storage.ObjectUrlConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_materials")
public class CourseMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String section;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private MaterialKind kind;

    @Column(name = "file_url")
    @Convert(converter = ObjectUrlConverter.class)
    private String fileUrl;

    @Column(name = "link_url", length = 2000)
    private String linkUrl;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseMaterial() {
    }

    public CourseMaterial(UUID courseId, MaterialKind kind, UUID createdBy) {
        this.courseId = courseId;
        this.kind = kind;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public UUID getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public MaterialKind getKind() { return kind; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
