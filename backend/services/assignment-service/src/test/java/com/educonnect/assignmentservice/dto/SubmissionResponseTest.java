package com.educonnect.assignmentservice.dto;

import com.educonnect.assignmentservice.model.AssignmentSubmission;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionResponseTest {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void keepsTheEntityJsonShapeIncludingLate() throws Exception {
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(UUID.randomUUID());
        submission.setAssignmentId(UUID.randomUUID());
        submission.setStudentId(UUID.randomUUID());
        submission.setSubmissionFileUrl("assignments-bucket/odev.pdf");
        submission.setLate(true);

        Map<String, Object> json = objectMapper.readValue(objectMapper.writeValueAsString(SubmissionResponse.from(submission)), JSON_MAP);

        assertThat(json.keySet()).containsExactlyInAnyOrder("id", "assignmentId", "studentId", "submissionFileUrl", "textContent",
                "submittedAt", "grade", "feedback", "late", "aiUsed", "aiNote", "createdAt", "updatedAt");
        assertThat(json.get("late")).isEqualTo(true);
    }
}
