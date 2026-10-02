package com.educonnect.assignmentservice.dto;

import java.time.LocalDateTime;

public record SubmissionVersionResponse(int versionNo, String fileUrl, String textContent, LocalDateTime submittedAt,
                                        boolean late) {
}
