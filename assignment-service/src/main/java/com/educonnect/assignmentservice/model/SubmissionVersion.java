package com.educonnect.assignmentservice.model;

import com.educonnect.common.storage.ObjectUrlConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "submission_versions",
        uniqueConstraints = @UniqueConstraint(name = "uq_submission_versions_number", columnNames = {"submission_id", "version_no"}))
public class SubmissionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "file_url")
    @Convert(converter = ObjectUrlConverter.class)
    private String fileUrl;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(nullable = false)
    private boolean late;

    protected SubmissionVersion() {
    }

    public SubmissionVersion(UUID submissionId, int versionNo, String fileUrl, String textContent,
                             LocalDateTime submittedAt, boolean late) {
        this.submissionId = submissionId;
        this.versionNo = versionNo;
        this.fileUrl = fileUrl;
        this.textContent = textContent;
        this.submittedAt = submittedAt;
        this.late = late;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public int getVersionNo() { return versionNo; }
    public String getFileUrl() { return fileUrl; }
    public String getTextContent() { return textContent; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public boolean isLate() { return late; }
}
