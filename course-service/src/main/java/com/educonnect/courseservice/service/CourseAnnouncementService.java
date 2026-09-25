package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.client.UserLookup;
import com.educonnect.courseservice.dto.AnnouncementRequest;
import com.educonnect.courseservice.dto.AnnouncementResponse;
import com.educonnect.courseservice.dto.UserSummaryDto;
import com.educonnect.courseservice.event.CourseNotificationEvent;
import com.educonnect.courseservice.exception.AnnouncementNotFoundException;
import com.educonnect.courseservice.exception.CourseNotFoundException;
import com.educonnect.courseservice.exception.UnauthorizedCourseAccessException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseAnnouncement;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.publisher.CourseProducer;
import com.educonnect.courseservice.repository.CourseAnnouncementRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourseAnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(CourseAnnouncementService.class);

    private final CourseAnnouncementRepository announcementRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseProducer courseProducer;
    private final UserClient userClient;

    public CourseAnnouncementService(CourseAnnouncementRepository announcementRepository,
                                      CourseRepository courseRepository,
                                      EnrollmentRepository enrollmentRepository,
                                      CourseProducer courseProducer,
                                      UserClient userClient) {
        this.announcementRepository = announcementRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseProducer = courseProducer;
        this.userClient = userClient;
    }

    /**
     * Hoca duyuru oluşturur ve kayıtlı öğrencilere bildirim gönderir.
     */
    @Transactional
    public AnnouncementResponse createAnnouncement(UUID courseId, AnnouncementRequest request, UUID instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));

        // Dersin hocası mı kontrol et
        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedCourseAccessException("Bu dersin hocası değilsiniz, duyuru paylaşamazsınız.");
        }

        // Duyuru oluştur
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setCourseId(courseId);
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setCreatedBy(instructorId);

        CourseAnnouncement saved = announcementRepository.save(announcement);

        log.info("📢 Duyuru oluşturuldu: {} -> Ders: {} ({})", request.getTitle(), course.getTitle(), course.getCode());

        // Kayıtlı öğrenci ID'lerini çek ve RabbitMQ ile bildirim gönder
        sendNotificationToEnrolledStudents(course, "ANNOUNCEMENT", request.getTitle(), request.getContent());

        return mapToResponse(saved, course);
    }

    public List<AnnouncementResponse> getAnnouncementsByCourse(UUID courseId, UUID viewerId, boolean viewerIsAdmin) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));

        boolean allowed = viewerIsAdmin
                || course.getInstructorId().equals(viewerId)
                || enrollmentRepository.existsByCourseIdAndStudentIdAndIsActive(courseId, viewerId, true);
        if (!allowed) {
            throw new UnauthorizedCourseAccessException("Bu dersin duyurularını görme yetkiniz yok.");
        }

        List<CourseAnnouncement> announcements = announcementRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
        Map<UUID, UserSummaryDto> authors = UserLookup.usersById(userClient,
                announcements.stream().map(CourseAnnouncement::getCreatedBy).toList());

        return announcements.stream()
                .map(a -> toResponse(a, course, authors.get(a.getCreatedBy())))
                .collect(Collectors.toList());
    }

    /**
     * Duyuru siler (hoca yetkisi kontrolü ile).
     */
    public void deleteAnnouncement(UUID announcementId, UUID instructorId) {
        CourseAnnouncement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException("Duyuru bulunamadı: " + announcementId));

        Course course = courseRepository.findById(announcement.getCourseId())
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + announcement.getCourseId()));

        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedCourseAccessException("Bu duyuruyu silme yetkiniz yok.");
        }

        announcementRepository.deleteById(announcementId);
        log.info("🗑️ Duyuru silindi: {}", announcementId);
    }

    /**
     * Kayıtlı öğrencilere RabbitMQ üzerinden bildirim gönderir.
     */
    private void sendNotificationToEnrolledStudents(Course course, String type, String title, String description) {
        List<StudentCourseEnrollment> enrollments = enrollmentRepository.findByCourseIdAndIsActive(course.getId(), true);
        List<UUID> studentIds = enrollments.stream()
                .map(StudentCourseEnrollment::getStudentId)
                .collect(Collectors.toList());

        if (studentIds.isEmpty()) {
            log.info("📭 Derste kayıtlı öğrenci yok, bildirim gönderilmedi.");
            return;
        }

        CourseNotificationEvent event = new CourseNotificationEvent(
                course.getId(),
                course.getTitle(),
                course.getCode(),
                type,
                title,
                description,
                studentIds
        );

        courseProducer.sendAnnouncementNotification(event);
        log.info("📤 Bildirim event'i kuyruğa alındı: {} öğrenciye {} bildirimi", studentIds.size(), type);
    }

    private AnnouncementResponse mapToResponse(CourseAnnouncement announcement, Course course) {
        UserSummaryDto author;
        try {
            author = userClient.getUserById(announcement.getCreatedBy());
        } catch (Exception e) {
            author = null;
        }
        return toResponse(announcement, course, author);
    }

    private AnnouncementResponse toResponse(CourseAnnouncement announcement, Course course, UserSummaryDto author) {
        AnnouncementResponse dto = new AnnouncementResponse();
        dto.setId(announcement.getId());
        dto.setCourseId(announcement.getCourseId());
        dto.setCourseTitle(course.getTitle());
        dto.setTitle(announcement.getTitle());
        dto.setContent(announcement.getContent());
        dto.setCreatedAt(announcement.getCreatedAt());
        dto.setCreatedBy(announcement.getCreatedBy());
        dto.setCreatedByName(author != null ? author.getFirstName() + " " + author.getLastName() : "Bilinmiyor");
        return dto;
    }
}

