package com.educonnect.courseservice.service;

import com.educonnect.common.web.NotFoundException;
import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.client.UserLookup;
import com.educonnect.courseservice.dto.*;
import com.educonnect.courseservice.event.CourseEvent;
import com.educonnect.courseservice.exception.*;
import com.educonnect.common.web.ConflictException;
import com.educonnect.courseservice.model.CatalogCourse;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.publisher.CourseProducer;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
    private final CourseCaches courseCaches;
    private final TermService termService;
    private final CatalogCourseService catalogCourseService;
    private final OfferingDetails offeringDetails;

    public CourseService(CourseRepository repo, EnrollmentRepository enrollRepo,
                         CourseApplicationRepository appRepo,
                         UserClient user, MinioService minio, CourseProducer producer,
                         CourseCaches courseCaches,
                         TermService termService,
                         CatalogCourseService catalogCourseService,
                         OfferingDetails offeringDetails) {
        this.courseRepository = repo;
        this.enrollmentRepository = enrollRepo;
        this.applicationRepository = appRepo;
        this.userClient = user;
        this.minioService = minio;
        this.courseProducer = producer;
        this.courseCaches = courseCaches;
        this.termService = termService;
        this.catalogCourseService = catalogCourseService;
        this.offeringDetails = offeringDetails;
    }

    // 1. DERS OLUŞTUR (Resim + Veri + RabbitMQ)
    @Transactional
    public CourseResponse createCourse(CourseRequest request, MultipartFile file) {
        Term term = request.getTermId() != null ? termService.find(request.getTermId()) : termService.current();
        if (term.hasEnded(termService.today())) {
            throw new ConflictException("TERM_ENDED", "Bitmiş bir döneme ders açılamaz.");
        }
        CatalogCourse catalog = catalogCourseService.findOrCreate(request.getCode(), request.getTitle(), request.getCredit(),
                request.getEcts(), request.getInstructorId());
        String section = request.getSection() != null ? request.getSection().toUpperCase(Locale.ROOT) : "1";
        if (courseRepository.existsByCatalogCourseIdAndTermIdAndSection(catalog.getId(), term.getId(), section)) {
            throw new DuplicateCourseCodeException(catalog.getCode() + " dersi " + term.label() + " döneminde "
                    + section + ". şubeyle zaten açılmış.");
        }

        String imageUrl = null;
        if (file != null && !file.isEmpty()) {
            imageUrl = minioService.uploadFile(file);
        }

        Course course = new Course();
        course.setTitle(catalog.getTitle());
        course.setCode(catalog.getCode());
        course.setDescription(request.getDescription());
        course.setCredit(catalog.getCredit());
        course.setSemester(term.label());
        course.setTermId(term.getId());
        course.setCatalogCourseId(catalog.getId());
        course.setSection(section);
        course.setInstructorId(request.getInstructorId());
        course.setCapacity(request.getCapacity());
        course.setImageUrl(imageUrl);

        Course savedCourse = courseRepository.save(course);

        // instructorCourses cache'ini temizle
        courseCaches.evictInstructorCourses(request.getInstructorId());

        return mapToResponse(savedCourse);
    }

    // 2. TÜMÜNÜ GETİR
    public List<CourseResponse> getAllCourses(UUID termId) {
        return mapToResponses(termId != null ? courseRepository.findByTermId(termId) : courseRepository.findAll());
    }

    public PageResponse<CourseResponse> getCoursesPage(int page, Integer size, UUID termId) {
        Pageable pageable = PageResponse.request(page, size, Sort.by("title").and(Sort.by("id")));
        Page<Course> courses = termId != null ? courseRepository.findByTermId(termId, pageable) : courseRepository.findAll(pageable);
        return PageResponse.of(courses, mapToResponses(courses.getContent()));
    }

    // 3. ID İLE GETİR
    public CourseResponse getCourseById(UUID id) {
        return mapToResponse(courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + id)));
    }

    // 4. HOCAYA GÖRE GETİR
    public List<CourseResponse> getCoursesByInstructor(UUID instructorId) {
        return mapToResponses(courseRepository.findByInstructorId(instructorId));
    }

    // 5. SİL (RabbitMQ Tetikler)
    @Transactional
    public void deleteCourse(UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + id));
        courseCaches.evictStudentCourses(getEnrolledStudentIds(id));
        courseRepository.deleteById(id);

        CourseEvent event = new CourseEvent(course.getId(), course.getTitle(), course.getCode(), "DELETED");
        courseProducer.sendCourseDeletedEvent(event);

        // instructorCourses cache'ini temizle
        courseCaches.evictInstructorCourses(course.getInstructorId());
    }

    // 7. ÖĞRENCİNİN KAYITLI OLDUĞU KURSLARI GETİR (Cache'li)
    @Cacheable(value = "studentCourses", key = "#studentId")
    public List<EnrolledCourseDTO> getStudentCourses(UUID studentId) {
        List<StudentCourseEnrollment> enrollments = enrollmentRepository.findByStudentIdAndIsActive(studentId, true);
        List<UUID> courseIds = enrollments.stream().map(StudentCourseEnrollment::getCourseId).distinct().toList();
        Map<UUID, Course> courses = courseIds.isEmpty() ? Map.of() : courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        Map<UUID, UserSummaryDto> instructors = UserLookup.usersById(userClient,
                courses.values().stream().map(Course::getInstructorId).toList());
        OfferingDetails.Lookup offerings = offeringDetails.lookup(courses.values());

        return enrollments.stream().map(enrollment -> {
            Course course = courses.get(enrollment.getCourseId());
            if (course == null) return null;

            EnrolledCourseDTO dto = new EnrolledCourseDTO();
            dto.setId(course.getId());
            dto.setTitle(course.getTitle());
            dto.setCode(course.getCode());
            dto.setDescription(course.getDescription());
            dto.setCredit(course.getCredit());
            dto.setSemester(course.getSemester());
            offerings.apply(dto, course);
            dto.setImageUrl(course.getImageUrl());
            dto.setInstructorId(course.getInstructorId());
            dto.setEnrollmentDate(enrollment.getEnrollmentDate());
            dto.setInstructorName(instructorName(instructors.get(course.getInstructorId())));

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
            courseCaches.evictInstructorCourses(course.getInstructorId());
        }
    }

    // 9. AKADEMİSYENİN DERSLERİNİ GETİR (Cache'li + öğrenci sayısı + kapasite)
    @Cacheable(value = "instructorCourses", key = "#instructorId")
    public List<InstructorCourseDTO> getInstructorCourses(UUID instructorId) {
        List<Course> courses = courseRepository.findByInstructorId(instructorId);
        List<UUID> courseIds = courses.stream().map(Course::getId).toList();
        Map<UUID, Long> enrolledCounts = courseIds.isEmpty() ? Map.of()
                : toCountMap(enrollmentRepository.countActiveByCourseIds(courseIds));
        Map<UUID, Long> pendingCounts = courseIds.isEmpty() ? Map.of()
                : toCountMap(applicationRepository.countByCourseIdsAndStatus(courseIds, CourseApplicationStatus.PENDING));
        OfferingDetails.Lookup offerings = offeringDetails.lookup(courses);

        return courses.stream().map(course -> {
            InstructorCourseDTO dto = new InstructorCourseDTO();
            dto.setId(course.getId());
            dto.setTitle(course.getTitle());
            dto.setCode(course.getCode());
            dto.setDescription(course.getDescription());
            dto.setCredit(course.getCredit());
            dto.setSemester(course.getSemester());
            offerings.apply(dto, course);
            dto.setImageUrl(course.getImageUrl());
            dto.setCapacity(course.getCapacity());
            dto.setEnrolledStudentCount(enrolledCounts.getOrDefault(course.getId(), 0L));
            dto.setPendingApplicationCount(pendingCounts.getOrDefault(course.getId(), 0L));
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
        Map<UUID, UserSummaryDto> students = UserLookup.usersById(userClient,
                enrollments.stream().map(StudentCourseEnrollment::getStudentId).toList());

        return enrollments.stream().map(enrollment -> {
            EnrolledStudentDTO dto = new EnrolledStudentDTO();
            dto.setStudentId(enrollment.getStudentId());
            dto.setEnrollmentDate(enrollment.getEnrollmentDate());

            UserSummaryDto user = students.get(enrollment.getStudentId());
            if (user != null) {
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setStudentNumber(user.getStudentNumber());
                dto.setEmail(user.getEmail());
                dto.setDepartment(user.getDepartment());
            } else {
                dto.setFirstName("Bilinmiyor");
                dto.setLastName("");
            }

            return dto;
        }).collect(Collectors.toList());
    }

    public List<UUID> getInstructorCourseIds(UUID instructorId) {
        return courseRepository.findByInstructorId(instructorId).stream().map(Course::getId).toList();
    }

    public List<UUID> getActiveCourseIds(UUID studentId) {
        return enrollmentRepository.findByStudentIdAndIsActive(studentId, true).stream()
                .map(StudentCourseEnrollment::getCourseId)
                .distinct()
                .toList();
    }

    // 11. KAYITLI ÖĞRENCİ ID LİSTESİ (Diğer servisler için - notification-service vs.)
    public List<UUID> getEnrolledStudentIds(UUID courseId) {
        List<StudentCourseEnrollment> enrollments = enrollmentRepository.findByCourseIdAndIsActive(courseId, true);
        return enrollments.stream()
                .map(StudentCourseEnrollment::getStudentId)
                .collect(Collectors.toList());
    }

    // 12. DOSYA İNDİRME
    public String getCourseFileUrl(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));
        if (course.getImageUrl() == null || course.getImageUrl().isBlank()) {
            throw new NotFoundException("FILE_NOT_FOUND", "Dosya bulunamadı.");
        }
        return course.getImageUrl();
    }

    public String requireCourseFileUrl(String fileUrl) {
        String canonical = minioService.canonicalUrl(fileUrl);
        if (canonical == null || !courseRepository.existsByImageUrl(canonical)) {
            throw new NotFoundException("FILE_NOT_FOUND", "Dosya bulunamadı.");
        }
        return canonical;
    }

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

    private List<CourseResponse> mapToResponses(List<Course> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> enrolledCounts = toCountMap(enrollmentRepository.countActiveByCourseIds(
                courses.stream().map(Course::getId).toList()));
        Map<UUID, UserSummaryDto> instructors = UserLookup.usersById(userClient,
                courses.stream().map(Course::getInstructorId).toList());
        OfferingDetails.Lookup offerings = offeringDetails.lookup(courses);

        return courses.stream().map(course -> {
            CourseResponse res = new CourseResponse();
            res.setId(course.getId());
            res.setTitle(course.getTitle());
            res.setCode(course.getCode());
            res.setDescription(course.getDescription());
            res.setCredit(course.getCredit());
            res.setSemester(course.getSemester());
            offerings.apply(res, course);
            res.setImageUrl(course.getImageUrl());
            res.setInstructorId(course.getInstructorId());
            res.setCapacity(course.getCapacity());
            res.setEnrolledStudentCount(enrolledCounts.getOrDefault(course.getId(), 0L));
            res.setInstructorName(instructorName(instructors.get(course.getInstructorId())));
            return res;
        }).collect(Collectors.toList());
    }

    private static Map<UUID, Long> toCountMap(List<EnrollmentRepository.CourseCount> counts) {
        return counts.stream().collect(Collectors.toMap(EnrollmentRepository.CourseCount::getCourseId,
                EnrollmentRepository.CourseCount::getTotal));
    }

    private static String instructorName(UserSummaryDto user) {
        return user != null ? user.getFirstName() + " " + user.getLastName() : "Bilinmiyor";
    }

    private CourseResponse mapToResponse(Course course) {
        CourseResponse res = new CourseResponse();
        res.setId(course.getId());
        res.setTitle(course.getTitle());
        res.setCode(course.getCode());
        res.setDescription(course.getDescription());
        res.setCredit(course.getCredit());
        res.setSemester(course.getSemester());
        offeringDetails.lookup(List.of(course)).apply(res, course);
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
}
