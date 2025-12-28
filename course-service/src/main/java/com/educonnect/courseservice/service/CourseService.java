package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.dto.*;
import com.educonnect.courseservice.event.CourseEvent;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.publisher.CourseProducer;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserClient userClient;
    private final MinioService minioService;
    private final CourseProducer courseProducer;

    public CourseService(CourseRepository repo, EnrollmentRepository enrollRepo, UserClient user, MinioService minio, CourseProducer producer) {
        this.courseRepository = repo;
        this.enrollmentRepository = enrollRepo;
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

    // 6. ÖĞRENCİ KURSA KAYDET (Akademisyen tarafından)
    @CacheEvict(value = "studentCourses", key = "#studentId")
    public void enrollStudent(UUID courseId, UUID studentId, UUID instructorId) {
        // Ders var mı kontrol et
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Ders bulunamadı"));

        // Akademisyen bu dersin hocası mı kontrol et
        if (!course.getInstructorId().equals(instructorId)) {
            throw new RuntimeException("Bu dersin hocası değilsiniz, öğrenci ekleyemezsiniz");
        }

        // Öğrenci zaten kayıtlı mı kontrol et
        if (enrollmentRepository.existsByCourseIdAndStudentIdAndIsActive(courseId, studentId, true)) {
            throw new RuntimeException("Öğrenci bu derse zaten kayıtlı");
        }

        // Kayıt oluştur (otomatik onaylı)
        StudentCourseEnrollment enrollment = new StudentCourseEnrollment(courseId, studentId);
        enrollmentRepository.save(enrollment);
    }

    // 7. ÖĞRENCİNİN KAYITLI OLDUĞU KURSLARI GETİR (Cache'li)
    @Cacheable(value = "studentCourses", key = "#studentId")
    public List<EnrolledCourseDTO> getStudentCourses(UUID studentId) {
        List<StudentCourseEnrollment> enrollments = enrollmentRepository.findByStudentIdAndIsActive(studentId, true);

        return enrollments.stream().map(enrollment -> {
            Course course = courseRepository.findById(enrollment.getCourseId())
                    .orElse(null);
            if (course == null) return null;

            EnrolledCourseDTO dto = new EnrolledCourseDTO();
            dto.setId(course.getId());
            dto.setTitle(course.getTitle());
            dto.setCode(course.getCode());
            dto.setDescription(course.getDescription());
            dto.setCredit(course.getCredit());
            dto.setSemester(course.getSemester());
            dto.setImageUrl(course.getImageUrl());
            dto.setInstructorId(course.getInstructorId());
            dto.setEnrollmentDate(enrollment.getEnrollmentDate());

            try {
                UserSummaryDto user = userClient.getUserById(course.getInstructorId());
                dto.setInstructorName(user.getFirstName() + " " + user.getLastName());
            } catch (Exception e) {
                dto.setInstructorName("Bilinmiyor");
            }

            return dto;
        }).filter(dto -> dto != null).collect(Collectors.toList());
    }

    // 8. ÖĞRENCİ KURSTAN ÇIK (Soft Delete)
    @CacheEvict(value = "studentCourses", key = "#studentId")
    public void withdrawStudent(UUID courseId, UUID studentId) {
        StudentCourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new RuntimeException("Kayıt bulunamadı"));

        enrollment.setActive(false);
        enrollmentRepository.save(enrollment);
    }

    // 9. AKADEMİSYENİN DERSLERİNİ GETİR (Cache'li + öğrenci sayısı)
    @Cacheable(value = "instructorCourses", key = "#instructorId")
    public List<InstructorCourseDTO> getInstructorCourses(UUID instructorId) {
        List<Course> courses = courseRepository.findByInstructorId(instructorId);

        return courses.stream().map(course -> {
            InstructorCourseDTO dto = new InstructorCourseDTO();
            dto.setId(course.getId());
            dto.setTitle(course.getTitle());
            dto.setCode(course.getCode());
            dto.setDescription(course.getDescription());
            dto.setCredit(course.getCredit());
            dto.setSemester(course.getSemester());
            dto.setImageUrl(course.getImageUrl());
            dto.setEnrolledStudentCount(enrollmentRepository.countActiveByCourseId(course.getId()));
            return dto;
        }).collect(Collectors.toList());
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