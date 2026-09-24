package com.educonnect.assignmentservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "course-service", contextId = "courseInternalClient", path = "/api/courses/internal",
        configuration = ServiceTokenFeignConfiguration.class)
public interface CourseInternalClient {

    @GetMapping("/{courseId}/enrolled-students/ids")
    List<UUID> getEnrolledStudentIds(@PathVariable("courseId") UUID courseId);
}
