package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.dto.*;
import com.educonnect.courseservice.event.CourseEvent;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.publisher.CourseProducer;
import com.educonnect.courseservice.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final UserClient userClient;
    private final MinioService minioService;
    private final CourseProducer courseProducer;

    public CourseService(CourseRepository repo, UserClient user, MinioService minio, CourseProducer producer) {
        this.courseRepository = repo;
        this.userClient = user;
        this.minioService = minio;
        this.courseProducer = producer;
    }

    // 1. DERS OLUŞTUR (Resim + Veri + RabbitMQ)
    public CourseResponse createCourse(CourseRequest request, MultipartFile file) {
        if (courseRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Ders kodu zaten mevcut: " + request.getCode());
        }

        String imageUrl = null;
        if (file != null && !file.isEmpty()) {
            imageUrl = minioService.uploadFile(file);
        }

        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setCode(request.getCode());
        course.setDescription(request.getDescription());
        course.setCredit(request.getCredit());
        course.setSemester(request.getSemester());
        course.setInstructorId(request.getInstructorId());
        course.setImageUrl(imageUrl);

        Course savedCourse = courseRepository.save(course);

        // RabbitMQ Bildirimi
        CourseEvent event = new CourseEvent(savedCourse.getId(), savedCourse.getTitle(), savedCourse.getCode(), "CREATED");
        courseProducer.sendCourseCreatedEvent(event);

        return mapToResponse(savedCourse);
    }

    // 2. TÜMÜNÜ GETİR
    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // 3. ID İLE GETİR
    public CourseResponse getCourseById(UUID id) {
        return mapToResponse(courseRepository.findById(id).orElseThrow(() -> new RuntimeException("Ders yok")));
    }

    // 4. HOCAYA GÖRE GETİR
    public List<CourseResponse> getCoursesByInstructor(UUID instructorId) {
        return courseRepository.findByInstructorId(instructorId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // 5. SİL (RabbitMQ Tetikler)
    public void deleteCourse(UUID id) {
        Course course = courseRepository.findById(id).orElseThrow(() -> new RuntimeException("Ders yok"));
        courseRepository.deleteById(id);

        CourseEvent event = new CourseEvent(course.getId(), course.getTitle(), course.getCode(), "DELETED");
        courseProducer.sendCourseDeletedEvent(event);
    }

    private CourseResponse mapToResponse(Course course) {
        CourseResponse res = new CourseResponse();
        res.setId(course.getId());
        res.setTitle(course.getTitle());
        res.setCode(course.getCode());
        res.setDescription(course.getDescription());
        res.setCredit(course.getCredit());
        res.setSemester(course.getSemester());
        res.setImageUrl(course.getImageUrl());
        res.setInstructorId(course.getInstructorId());

        try {
            UserSummaryDto user = userClient.getUserById(course.getInstructorId());
            res.setInstructorName(user.getFirstName() + " " + user.getLastName());
        } catch (Exception e) {
            res.setInstructorName("Bilinmiyor");
        }
        return res;
    }
}