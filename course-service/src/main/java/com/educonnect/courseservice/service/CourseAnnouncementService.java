package com.educonnect.courseservice.service;

import com.educonnect.courseservice.client.UserClient;
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

import java.util.List;
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

    /**
     * Bir derse ait duyuruları listeler.
     */
    public List<AnnouncementResponse> getAnnouncementsByCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));

        return announcementRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(a -> mapToResponse(a, course))
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
        try {
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
            log.info("📤 Bildirim event'i gönderildi: {} öğrenciye {} bildirimi", studentIds.size(), type);
        } catch (Exception e) {
            log.error("❌ Bildirim gönderme hatası: {}", e.getMessage());
        }
    }

    private AnnouncementResponse mapToResponse(CourseAnnouncement announcement, Course course) {
        AnnouncementResponse dto = new AnnouncementResponse();
        dto.setId(announcement.getId());
        dto.setCourseId(announcement.getCourseId());
        dto.setCourseTitle(course.getTitle());
        dto.setTitle(announcement.getTitle());
        dto.setContent(announcement.getContent());
        dto.setCreatedAt(announcement.getCreatedAt());
        dto.setCreatedBy(announcement.getCreatedBy());

        try {
            UserSummaryDto user = userClient.getUserById(announcement.getCreatedBy());
            dto.setCreatedByName(user.getFirstName() + " " + user.getLastName());
        } catch (Exception e) {
            dto.setCreatedByName("Bilinmiyor");
        }

        return dto;
    }
}

