package com.educonnect.assignmentservice.dto;

import java.time.Instant;
import java.util.UUID;

public record SubmissionVersionResponse(int versionNo, String fileUrl, String textContent, Instant submittedAt,
                                        boolean late, UUID submittedBy) {
}
