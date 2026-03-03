package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.dto.*;
import com.educonnect.courseservice.event.CourseEvent;
import com.educonnect.courseservice.exception.*;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.publisher.CourseProducer;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseApplicationRepository applicationRepository;
    private final UserClient userClient;
    private final MinioService minioService;
    private final CourseProducer courseProducer;
    private final CacheManager cacheManager;

    public CourseService(CourseRepository repo, EnrollmentRepository enrollRepo,
                         CourseApplicationRepository appRepo,
                         UserClient user, MinioService minio, CourseProducer producer,
                         CacheManager cacheManager) {
        this.courseRepository = repo;
        this.enrollmentRepository = enrollRepo;
        this.applicationRepository = appRepo;
        this.userClient = user;
        this.minioService = minio;
        this.courseProducer = producer;
        this.cacheManager = cacheManager;
    }

    // 1. DERS OLUŞTUR (Resim + Veri + RabbitMQ)
    @Transactional
    public CourseResponse createCourse(CourseRequest request, MultipartFile file) {
        if (courseRepository.existsByCode(request.getCode())) {
            throw new DuplicateCourseCodeException("Ders kodu zaten mevcut: " + request.getCode());
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
        course.setCapacity(request.getCapacity());
        course.setImageUrl(imageUrl);

        Course savedCourse = courseRepository.save(course);

        // RabbitMQ Bildirimi
        CourseEvent event = new CourseEvent(savedCourse.getId(), savedCourse.getTitle(), savedCourse.getCode(), "CREATED");
        courseProducer.sendCourseCreatedEvent(event);

        // instructorCourses cache'ini temizle
        evictInstructorCoursesCache(request.getInstructorId());

        return mapToResponse(savedCourse);
    }

    // 2. TÜMÜNÜ GETİR
    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // 3. ID İLE GETİR
    public CourseResponse getCourseById(UUID id) {
        return mapToResponse(courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + id)));
    }

    // 4. HOCAYA GÖRE GETİR
    public List<CourseResponse> getCoursesByInstructor(UUID instructorId) {
        return courseRepository.findByInstructorId(instructorId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // 5. SİL (RabbitMQ Tetikler)
    @Transactional
    public void deleteCourse(UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + id));
        courseRepository.deleteById(id);

        CourseEvent event = new CourseEvent(course.getId(), course.getTitle(), course.getCode(), "DELETED");
        courseProducer.sendCourseDeletedEvent(event);

        // instructorCourses cache'ini temizle
        evictInstructorCoursesCache(course.getInstructorId());
    }

    // 6. ÖĞRENCİ KURSA KAYDET (Akademisyen tarafından - doğrudan ekleme)
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "studentCourses", key = "#studentId")
    })
    public void enrollStudent(UUID courseId, UUID studentId, UUID instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));

        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedCourseAccessException("Bu dersin hocası değilsiniz, öğrenci ekleyemezsiniz");
        }

        if (enrollmentRepository.existsByCourseIdAndStudentIdAndIsActive(courseId, studentId, true)) {
            throw new AlreadyEnrolledException("Öğrenci bu derse zaten kayıtlı");
        }

        // Kapasite kontrolü
        long enrolledCount = enrollmentRepository.countActiveByCourseId(courseId);
        if (enrolledCount >= course.getCapacity()) {
            throw new CourseCapacityFullException("Ders kapasitesi dolmuş. Öğrenci eklenemez.");
        }

        StudentCourseEnrollment enrollment = new StudentCourseEnrollment(courseId, studentId);
        enrollmentRepository.save(enrollment);

        // instructorCourses cache'ini temizle (öğrenci sayısı değişti)
        evictInstructorCoursesCache(course.getInstructorId());
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
    @Transactional
    @CacheEvict(value = "studentCourses", key = "#studentId")
    public void withdrawStudent(UUID courseId, UUID studentId) {
        StudentCourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new EnrollmentNotFoundException("Kayıt bulunamadı"));

        enrollment.setActive(false);
        enrollmentRepository.save(enrollment);

        // instructorCourses cache'ini temizle (öğrenci sayısı değişti)
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course != null) {
            evictInstructorCoursesCache(course.getInstructorId());
        }
    }

    // 9. AKADEMİSYENİN DERSLERİNİ GETİR (Cache'li + öğrenci sayısı + kapasite)
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
            dto.setCapacity(course.getCapacity());
            dto.setEnrolledStudentCount(enrollmentRepository.countActiveByCourseId(course.getId()));
            dto.setPendingApplicationCount(
                    applicationRepository.countByCourseIdAndStatus(course.getId(), CourseApplicationStatus.PENDING));
            return dto;
        }).collect(Collectors.toList());
    }

    // 10. KAYITLI ÖĞRENCİ LİSTESİ (Hoca için - detaylı bilgiyle)
    public List<EnrolledStudentDTO> getEnrolledStudents(UUID courseId, UUID instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));

        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedCourseAccessException("Bu dersin hocası değilsiniz.");
        }

        List<StudentCourseEnrollment> enrollments = enrollmentRepository.findByCourseIdAndIsActive(courseId, true);

        return enrollments.stream().map(enrollment -> {
            EnrolledStudentDTO dto = new EnrolledStudentDTO();
            dto.setStudentId(enrollment.getStudentId());
            dto.setEnrollmentDate(enrollment.getEnrollmentDate());

            try {
                UserSummaryDto user = userClient.getUserById(enrollment.getStudentId());
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setStudentNumber(user.getStudentNumber());
                dto.setEmail(user.getEmail());
                dto.setDepartment(user.getDepartment());
            } catch (Exception e) {
                dto.setFirstName("Bilinmiyor");
                dto.setLastName("");
            }

            return dto;
        }).collect(Collectors.toList());
    }

    // 11. KAYITLI ÖĞRENCİ ID LİSTESİ (Diğer servisler için - notification-service vs.)
    public List<UUID> getEnrolledStudentIds(UUID courseId) {
        List<StudentCourseEnrollment> enrollments = enrollmentRepository.findByCourseIdAndIsActive(courseId, true);
        return enrollments.stream()
                .map(StudentCourseEnrollment::getStudentId)
                .collect(Collectors.toList());
    }

    // 12. DOSYA İNDİRME
    public Resource downloadFile(String fileUrl) {
        InputStream inputStream = minioService.downloadFile(fileUrl);
        return new InputStreamResource(inputStream);
    }

    /**
     * Dosya URL'inden orijinal dosya adını çıkarır.
     */
    public String getOriginalFileName(String fileUrl) {
        return minioService.extractOriginalFileName(fileUrl);
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
        res.setCapacity(course.getCapacity());
        res.setEnrolledStudentCount(enrollmentRepository.countActiveByCourseId(course.getId()));

        try {
            UserSummaryDto user = userClient.getUserById(course.getInstructorId());
            res.setInstructorName(user.getFirstName() + " " + user.getLastName());
        } catch (Exception e) {
            res.setInstructorName("Bilinmiyor");
        }
        return res;
    }

    /**
     * instructorCourses cache'ini programatik olarak temizler.
     * Ders oluşturma, silme, öğrenci kayıt/çıkış işlemlerinde kullanılır.
     */
    private void evictInstructorCoursesCache(UUID instructorId) {
        try {
            var cache = cacheManager.getCache("instructorCourses");
            if (cache != null) {
                cache.evict(instructorId);
                log.debug("instructorCourses cache temizlendi: {}", instructorId);
            }
        } catch (Exception e) {
            log.warn("instructorCourses cache temizleme hatası: {}", e.getMessage());
        }
    }
}