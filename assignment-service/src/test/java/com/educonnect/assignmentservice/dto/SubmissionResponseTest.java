package com.educonnect.assignmentservice.dto;

import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionResponseTest {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Test
    void keepsTheEntityJsonShapeIncludingLate() throws Exception {
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(UUID.randomUUID());
        submission.setAssignmentId(UUID.randomUUID());
        submission.setStudentId(UUID.randomUUID());
        submission.setSubmissionFileUrl("assignments-bucket/odev.pdf");
        submission.setLate(true);

        Map<String, Object> json = objectMapper.readValue(objectMapper.writeValueAsString(SubmissionResponse.from(submission)), JSON_MAP);

        assertThat(json.keySet()).containsExactlyInAnyOrder("id", "assignmentId", "studentId", "submissionFileUrl",
                "submittedAt", "grade", "feedback", "late", "createdAt", "updatedAt");
        assertThat(json.get("late")).isEqualTo(true);
    }
}
