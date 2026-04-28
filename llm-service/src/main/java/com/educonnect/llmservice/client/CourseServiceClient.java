package com.educonnect.llmservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

import com.educonnect.llmservice.dto.InstructorCourseSummary;

// name = "course-service" kısmı Eureka'daki kayıtlı adıdır. Load Balancer arka planda IP'yi otomatik bulur.
@FeignClient(name = "course-service", path = "/api/courses")
public interface CourseServiceClient {

    // İç içe geçmiş sınıflar (Record) kullanarak DTO karmaşasını engelliyoruz (Clean Code).
    record AnnouncementRequest(String title, String content) {}

    @PostMapping("/{courseId}/announcements")
    void createAnnouncement(
            @RequestHeader("X-Authenticated-User-Id") String instructorId,
            @PathVariable("courseId") String courseId,
            @RequestBody AnnouncementRequest request
    );

    @GetMapping("/instructor/me/courses")
    List<InstructorCourseSummary> getMyInstructorCourses(
            @RequestHeader("X-Authenticated-User-Id") String instructorId
    );
}