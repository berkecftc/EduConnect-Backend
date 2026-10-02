package com.educonnect.userservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "course-service", contextId = "courseRelationClient", path = "/api/courses/internal", configuration = ServiceTokenFeignConfiguration.class)
public interface CourseRelationClient {

    @GetMapping("/staff/{viewerId}/students/{studentId}")
    Map<String, Boolean> teachesStudent(@PathVariable("viewerId") UUID viewerId, @PathVariable("studentId") UUID studentId);
}
