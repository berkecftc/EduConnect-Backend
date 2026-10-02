package com.educonnect.assignmentservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubmissionVersionResponse(int versionNo, String fileUrl, String textContent, LocalDateTime submittedAt,
                                        boolean late, UUID submittedBy) {
}
