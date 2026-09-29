package com.educonnect.assignmentservice.config;

import com.educonnect.assignmentservice.dto.MyAssignmentDTO;
import com.educonnect.assignmentservice.dto.MySubmissionDTO;
import com.educonnect.common.web.cache.CacheValueSerializers;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheConfigTest {

    private final GenericJacksonJsonRedisSerializer serializer = CacheValueSerializers.typed();

    @Test
    void cachedAssignmentsKeepTheirSubmission() {
        MySubmissionDTO submission = new MySubmissionDTO();
        submission.setSubmissionId(UUID.randomUUID());
        submission.setSubmittedAt(LocalDateTime.of(2026, 10, 2, 23, 50));
        submission.setGrade(85);
        submission.setLate(true);
        MyAssignmentDTO assignment = new MyAssignmentDTO();
        assignment.setId(UUID.randomUUID());
        assignment.setTitle("Ödev 1");
        assignment.setDueDate(LocalDateTime.of(2026, 10, 1, 23, 59));
        assignment.setSubmission(submission);

        Object restored = serializer.deserialize(serializer.serialize(new ArrayList<>(List.of(assignment))));

        MyAssignmentDTO cached = (MyAssignmentDTO) ((List<?>) restored).get(0);
        assertThat(cached.getId()).isEqualTo(assignment.getId());
        assertThat(cached.getDueDate()).isEqualTo(assignment.getDueDate());
        assertThat(cached.getSubmission().getGrade()).isEqualTo(85);
        assertThat(cached.getSubmission().isLate()).isTrue();
    }
}
