package com.educonnect.courseservice.config;

import com.educonnect.common.web.cache.CacheValueSerializers;
import com.educonnect.courseservice.dto.EnrolledCourseDTO;
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
    void cachedCoursesComeBackAsCourseObjects() {
        EnrolledCourseDTO course = new EnrolledCourseDTO();
        course.setId(UUID.randomUUID());
        course.setCode("SEED101");
        course.setCredit(4);
        course.setInstructorId(UUID.randomUUID());
        course.setEnrollmentDate(LocalDateTime.of(2026, 9, 15, 9, 30));

        Object restored = serializer.deserialize(serializer.serialize(new ArrayList<>(List.of(course))));

        EnrolledCourseDTO cached = (EnrolledCourseDTO) ((List<?>) restored).get(0);
        assertThat(cached.getId()).isEqualTo(course.getId());
        assertThat(cached.getCode()).isEqualTo("SEED101");
        assertThat(cached.getCredit()).isEqualTo(4);
        assertThat(cached.getEnrollmentDate()).isEqualTo(course.getEnrollmentDate());
    }
}
