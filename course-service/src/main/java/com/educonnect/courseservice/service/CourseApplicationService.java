package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.client.UserLookup;
import com.educonnect.courseservice.dto.CourseApplicationResponse;
import com.educonnect.courseservice.dto.UserSummaryDto;
import com.educonnect.courseservice.exception.*;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseApplication;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CourseApplicationService.class);

    private final CourseApplicationRepository applicationRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserClient userClient;
    private final CourseCaches courseCaches;

    public CourseApplicationService(CourseApplicationRepository applicationRepository,
                                     CourseRepository courseRepository,
                                     EnrollmentRepository enrollmentRepository,
                                     UserClient userClient,
                                     CourseCaches courseCaches) {
        this.applicationRepository = applicationRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userClient = userClient;
        this.courseCaches = courseCaches;
    }

    /**
     * Öğrenci derse başvuru yapar.
     * Kapasite kontrolü, mükerrer başvuru ve zaten kayıtlı olma kontrolü yapılır.
     */
    @Transactional
    public CourseApplicationResponse applyToCourse(UUID courseId, UUID studentId) {
        // 1. Ders var mı kontrol et
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));

        // 2. Öğrenci zaten bu derse kayıtlı mı?
        if (enrollmentRepository.existsByCourseIdAndStudentIdAndIsActive(courseId, studentId, true)) {
            throw new AlreadyEnrolledException("Bu derse zaten kayıtlısınız.");
        }

        // 3. Öğrencinin zaten bekleyen başvurusu var mı?
        CourseApplication application = applicationRepository.findByCourseIdAndStudentId(courseId, studentId)
                .orElse(null);
        if (application != null && application.getStatus() == CourseApplicationStatus.PENDING) {
            throw new DuplicateApplicationException("Bu derse zaten bekleyen bir başvurunuz var.");
        }

        // 4. Kapasite doldu mu kontrol et (kayıtlı + onaylanmış başvurular)
        long enrolledCount = enrollmentRepository.countActiveByCourseId(courseId);
        if (enrolledCount >= course.getCapacity()) {
            throw new CourseCapacityFullException("Bu dersin kapasitesi dolmuştur. Başvuru yapılamaz.");
        }

        // 5. Başvuru oluştur
        if (application == null) {
            application = new CourseApplication(courseId, studentId);
        } else {
            application.setStatus(CourseApplicationStatus.PENDING);
            application.setApplicationDate(LocalDateTime.now());
            application.setProcessedDate(null);
            application.setProcessedBy(null);
            application.setRejectionReason(null);
        }
        CourseApplication saved = applicationRepository.save(application);
        courseCaches.evictInstructorCourses(course.getInstructorId());

        log.info("📝 Yeni ders başvurusu: Öğrenci {} -> Ders {} ({})", studentId, course.getTitle(), course.getCode());

        return mapToResponse(saved, course);
    }

    /**
     * Hocanın bekleyen başvuruları listeler (FCFS - tarih sırasına göre).
     */
    public List<CourseApplicationResponse> getPendingApplications(UUID courseId, UUID instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));

        // Dersin hocası mı kontrol et
        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedCourseAccessException("Bu dersin hocası değilsiniz.");
        }

        List<CourseApplication> pendingApps = applicationRepository
                .findByCourseIdAndStatusOrderByApplicationDateAsc(courseId, CourseApplicationStatus.PENDING);
        Map<UUID, UserSummaryDto> students = UserLookup.usersById(userClient,
                pendingApps.stream().map(CourseApplication::getStudentId).toList());

        return pendingApps.stream()
                .map(app -> mapToResponseWithStudentInfo(app, course, students.get(app.getStudentId())))
                .collect(Collectors.toList());
    }

    /**
     * Hoca tek tek başvuru onaylar.
     * Onay sırasında kapasite tekrar kontrol edilir.
     */
    @Transactional
    public CourseApplicationResponse approveApplication(UUID applicationId, UUID instructorId) {
        CourseApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException("Başvuru bulunamadı: " + applicationId));

        Course course = courseRepository.findByIdForUpdate(application.getCourseId())
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + application.getCourseId()));

        // Dersin hocası mı kontrol et
        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedCourseAccessException("Bu dersin hocası değilsiniz, başvuru onaylayamazsınız.");
        }

        // Başvuru zaten işlenmiş mi?
        if (application.getStatus() != CourseApplicationStatus.PENDING) {
            throw new ApplicationAlreadyProcessedException("Bu başvuru zaten işlenmiş. Durum: " + application.getStatus());
        }

        // Kapasite kontrolü
        long enrolledCount = enrollmentRepository.countActiveByCourseId(course.getId());
        if (enrolledCount >= course.getCapacity()) {
            throw new CourseCapacityFullException("Ders kapasitesi dolmuş. Onaylama yapılamaz.");
        }

        // Başvuruyu onayla
        application.setStatus(CourseApplicationStatus.APPROVED);
        application.setProcessedDate(LocalDateTime.now());
        application.setProcessedBy(instructorId);
        applicationRepository.save(application);

        // Enrollment oluştur
        StudentCourseEnrollment enrollment = enrollmentRepository
                .findByCourseIdAndStudentId(course.getId(), application.getStudentId())
                .orElseGet(() -> new StudentCourseEnrollment(course.getId(), application.getStudentId()));
        if (enrollment.getId() != null) {
            if (enrollment.isActive()) {
                throw new AlreadyEnrolledException("Öğrenci bu derse zaten kayıtlı.");
            }
            enrollment.setActive(true);
            enrollment.setEnrollmentDate(LocalDateTime.now());
        }
        enrollmentRepository.save(enrollment);
        courseCaches.evictStudentCourses(application.getStudentId());
        courseCaches.evictInstructorCourses(course.getInstructorId());

        log.info("✅ Başvuru onaylandı: Öğrenci {} -> Ders {} ({})",
                application.getStudentId(), course.getTitle(), course.getCode());

        return mapToResponse(application, course);
    }

    /**
     * Hoca başvuru reddeder.
     */
    @Transactional
    public CourseApplicationResponse rejectApplication(UUID applicationId, UUID instructorId, String rejectionReason) {
        CourseApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException("Başvuru bulunamadı: " + applicationId));

        Course course = courseRepository.findById(application.getCourseId())
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + application.getCourseId()));

        // Dersin hocası mı kontrol et
        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedCourseAccessException("Bu dersin hocası değilsiniz, başvuru reddedemezsiniz.");
        }

        // Başvuru zaten işlenmiş mi?
        if (application.getStatus() != CourseApplicationStatus.PENDING) {
            throw new ApplicationAlreadyProcessedException("Bu başvuru zaten işlenmiş. Durum: " + application.getStatus());
        }

        // Başvuruyu reddet
        application.setStatus(CourseApplicationStatus.REJECTED);
        application.setProcessedDate(LocalDateTime.now());
        application.setProcessedBy(instructorId);
        application.setRejectionReason(rejectionReason);
        applicationRepository.save(application);
        courseCaches.evictInstructorCourses(course.getInstructorId());

        log.info("❌ Başvuru reddedildi: Öğrenci {} -> Ders {} ({}). Sebep: {}",
                application.getStudentId(), course.getTitle(), course.getCode(), rejectionReason);

        return mapToResponse(application, course);
    }

    /**
     * Öğrencinin kendi başvurularını listeler.
     */
    public List<CourseApplicationResponse> getMyApplications(UUID studentId) {
        List<CourseApplication> applications = applicationRepository
                .findByStudentIdOrderByApplicationDateDesc(studentId);
        List<UUID> courseIds = applications.stream().map(CourseApplication::getCourseId).distinct().toList();
        Map<UUID, Course> courses = courseIds.isEmpty() ? Map.of() : courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        return applications.stream()
                .map(app -> mapToResponse(app, courses.get(app.getCourseId())))
                .collect(Collectors.toList());
    }

    // --- PRIVATE HELPER METHODS ---

    private CourseApplicationResponse mapToResponse(CourseApplication app, Course course) {
        CourseApplicationResponse dto = new CourseApplicationResponse();
        dto.setId(app.getId());
        dto.setCourseId(app.getCourseId());
        dto.setStudentId(app.getStudentId());
        dto.setStatus(app.getStatus());
        dto.setApplicationDate(app.getApplicationDate());
        dto.setProcessedDate(app.getProcessedDate());
        dto.setRejectionReason(app.getRejectionReason());

        if (course != null) {
            dto.setCourseTitle(course.getTitle());
            dto.setCourseCode(course.getCode());
        }

        return dto;
    }

    private CourseApplicationResponse mapToResponseWithStudentInfo(CourseApplication app, Course course, UserSummaryDto user) {
        CourseApplicationResponse dto = mapToResponse(app, course);

        if (user != null) {
            dto.setStudentName(user.getFirstName() + " " + user.getLastName());
            dto.setStudentNumber(user.getStudentNumber());
            dto.setStudentEmail(user.getEmail());
        } else {
            dto.setStudentName("Bilinmiyor");
        }

        return dto;
    }
}

