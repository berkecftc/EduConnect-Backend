package com.educonnect.postservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import com.educonnect.postservice.dto.CourseAccess;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "course-service", contextId = "postCourseClient", path = "/api/courses/internal",
        configuration = ServiceTokenFeignConfiguration.class)
public interface CourseClient {

    @GetMapping("/{courseId}/access/{userId}")
    CourseAccess getAccess(@PathVariable("courseId") UUID courseId, @PathVariable("userId") UUID userId);

    @GetMapping("/students/{studentId}/course-ids")
    List<UUID> getStudentCourseIds(@PathVariable("studentId") UUID studentId);

    @GetMapping("/instructors/{staffId}/course-ids")
    List<UUID> getStaffCourseIds(@PathVariable("staffId") UUID staffId);
}
