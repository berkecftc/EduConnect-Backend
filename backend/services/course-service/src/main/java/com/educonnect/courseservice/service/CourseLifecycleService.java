package com.educonnect.courseservice.service;

import com.educonnect.common.web.ConflictException;
import com.educonnect.courseservice.config.CourseLifecycleSettings;
import com.educonnect.courseservice.dto.CourseUpdateRequest;
import com.educonnect.courseservice.exception.CourseNotFoundException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import com.educonnect.courseservice.model.CourseStatus;
import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import com.educonnect.courseservice.repository.TermRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class CourseLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CourseLifecycleService.class);

    private final CourseRepository courseRepository;
    private final TermRepository termRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseApplicationRepository applicationRepository;
    private final CourseRemoval courseRemoval;
    private final CourseCaches courseCaches;
    private final CourseLifecycleSettings settings;
    private final CourseStaffAccess staffAccess;
    private final Clock clock = Clock.systemDefaultZone();

    public CourseLifecycleService(CourseRepository courseRepository,
                                  TermRepository termRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  CourseApplicationRepository applicationRepository,
                                  CourseRemoval courseRemoval,
                                  CourseCaches courseCaches,
                                  CourseLifecycleSettings settings,
                                  CourseStaffAccess staffAccess) {
        this.courseRepository = courseRepository;
        this.termRepository = termRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.applicationRepository = applicationRepository;
        this.courseRemoval = courseRemoval;
        this.courseCaches = courseCaches;
        this.settings = settings;
        this.staffAccess = staffAccess;
    }

    public static CourseStatus initialStatus(Term term, boolean draft, LocalDate today) {
        if (draft) {
            return CourseStatus.DRAFT;
        }
        return today.isBefore(term.getStartsOn()) ? CourseStatus.OPEN : CourseStatus.ACTIVE;
    }

    public Course publish(UUID courseId, UUID userId) {
        Course course = owned(courseId, userId);
        if (course.getStatus() != CourseStatus.DRAFT) {
            throw new ConflictException("COURSE_NOT_DRAFT", "Yalnızca taslak ders yayınlanabilir.");
        }
        Term term = termOf(course);
        if (term.hasEnded(today())) {
            throw new ConflictException("TERM_ENDED", "Dönemi bitmiş ders yayınlanamaz.");
        }
        course.setStatus(initialStatus(term, false, today()));
        return saved(course);
    }

    public Course update(UUID courseId, UUID userId, CourseUpdateRequest request) {
        Course course = owned(courseId, userId);
        requireEditable(course);
        if (request.description() != null) {
            course.setDescription(request.description().isBlank() ? null : request.description().strip());
        }
        if (request.capacity() != null) {
            long enrolled = enrollmentRepository.countActiveByCourseId(courseId);
            if (request.capacity() < enrolled) {
                throw new ConflictException("CAPACITY_BELOW_ENROLLED", "Kontenjan kayıtlı öğrenci sayısının altına indirilemez.");
            }
            course.setCapacity(request.capacity());
        }
        return saved(course);
    }

    public Course archive(UUID courseId, UUID userId) {
        Course course = owned(courseId, userId);
        if (course.getStatus() != CourseStatus.COMPLETED) {
            throw new ConflictException("COURSE_NOT_COMPLETED", "Yalnızca tamamlanmış ders arşivlenebilir.");
        }
        course.archive(Instant.now(clock));
        return saved(course);
    }

    public void requireDeletable(Course course) {
        boolean empty = enrollmentRepository.findByCourseIdAndIsActive(course.getId(), true).isEmpty()
                && !applicationRepository.existsByCourseId(course.getId());
        if (course.getStatus() != CourseStatus.DRAFT || !empty) {
            throw new ConflictException("COURSE_NOT_DELETABLE",
                    "Yalnızca kaydı ve başvurusu olmayan taslak ders silinebilir; diğer dersler dönem sonunda arşivlenir.");
        }
    }

    public static void requireEditable(Course course) {
        if (!course.getStatus().isEditable()) {
            throw new ConflictException("COURSE_READ_ONLY", "Tamamlanmış veya arşivlenmiş ders değiştirilemez.");
        }
    }

    public static void requireRunning(Course course) {
        if (!course.getStatus().isRunning()) {
            throw new ConflictException("COURSE_NOT_OPEN", "Ders şu anda başvuru ve kayıt işlemlerine açık değil.");
        }
    }

    public static void requireAnnouncements(Course course) {
        CourseStatus status = course.getStatus();
        if (status != CourseStatus.OPEN && status != CourseStatus.ACTIVE && status != CourseStatus.COMPLETED) {
            throw new ConflictException("COURSE_READ_ONLY", "Bu dersin duyuruları şu anda değiştirilemez.");
        }
    }

    @Scheduled(cron ="${educonnect.course.lifecycle-cron:0 15 3 * * *}")
    public void advanceScheduled() {
        log.info("Course lifecycle advanced: {}", advance(today()));
    }

    public Map<String, Integer> advance(LocalDate today) {
        List<Course> courses = courseRepository.findByStatusIn(EnumSet.of(CourseStatus.OPEN, CourseStatus.ACTIVE,
                CourseStatus.COMPLETED, CourseStatus.ARCHIVED));
        Map<UUID, Term> terms = termRepository.findAllById(courses.stream().map(Course::getTermId).distinct().toList()).stream()
                .collect(Collectors.toMap(Term::getId, Function.identity()));
        int started = 0;
        int completed = 0;
        int archived = 0;
        int purged = 0;
        int closedApplications = 0;
        Instant now = Instant.now(clock);
        for (Course course : courses) {
            Term term = terms.get(course.getTermId());
            if (term == null) {
                continue;
            }
            CourseStatus before = course.getStatus();
            if (before.isRunning() && term.hasEnded(today)) {
                course.complete(now);
                completed++;
                closedApplications += applicationRepository.closePending(course.getId(), CourseApplicationStatus.PENDING,
                        CourseApplicationStatus.CLOSED, Instant.now(clock));
            } else if (before == CourseStatus.OPEN && !today.isBefore(term.getStartsOn())) {
                course.setStatus(CourseStatus.ACTIVE);
                started++;
            } else if (before == CourseStatus.COMPLETED && today.isAfter(term.getEndsOn().plusDays(settings.archiveAfterDays()))) {
                course.archive(now);
                archived++;
            } else if (before == CourseStatus.ARCHIVED && !settings.retainsForever()
                    && today.isAfter(term.getEndsOn().plusYears(settings.retentionYears()))) {
                courseRemoval.remove(course);
                purged++;
                continue;
            }
            if (course.getStatus() != before) {
                courseRepository.save(course);
                courseCaches.evictStaffCourses(course);
            }
        }
        return Map.of("started", started, "completed", completed, "archived", archived, "purged", purged,
                "closedApplications", closedApplications);
    }

    private Course owned(UUID courseId, UUID userId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));
        staffAccess.requireCoordinator(course, userId);
        return course;
    }

    private Course saved(Course course) {
        Course saved = courseRepository.save(course);
        courseCaches.evictStaffCourses(course);
        return saved;
    }

    private Term termOf(Course course) {
        return termRepository.findById(course.getTermId())
                .orElseThrow(() -> new IllegalStateException("Term missing for course " + course.getId()));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
