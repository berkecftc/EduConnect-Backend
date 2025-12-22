package com.educonnect.assignmentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@FeignClient(name = "course-service", path = "/api/courses")
public interface CourseClient {
    // Sadece ders var mı yok mu ve temel bilgisi için
    @GetMapping("/{id}")
    Object getCourseById(@PathVariable("id") UUID id);
}