package com.educonnect.assignmentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "course-service", path = "/api/courses")
public interface CourseClient {
    // Sadece ders var mı yok mu ve temel bilgisi için
    @GetMapping("/{id}")
    Map<String, Object> getCourseById(@PathVariable("id") UUID id);

    // Kayıtlı öğrenci ID listesi
    @GetMapping("/{courseId}/enrolled-students/ids")
    List<UUID> getEnrolledStudentIds(@PathVariable("courseId") UUID courseId);
}