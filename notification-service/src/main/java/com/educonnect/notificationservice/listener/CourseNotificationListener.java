package com.educonnect.notificationservice.listener;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.notificationservice.config.NotificationRabbitMQConfig;
import com.educonnect.notificationservice.dto.message.CourseNotificationMessage;
import com.educonnect.notificationservice.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CourseNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationListener.class);

    private final NotificationDispatcher dispatcher;

    public CourseNotificationListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.COURSE_ANNOUNCEMENT_QUEUE)
    public void handleAnnouncementCreated(CourseNotificationMessage message) {
        log.info("Course announcement notification received: course={}", message.getCourseCode());
        notify(message, "COURSE_ANNOUNCEMENT", "Yeni Duyuru", "duyuru");
    }

    @RabbitListener(queues = NotificationRabbitMQConfig.COURSE_ASSIGNMENT_QUEUE)
    public void handleAssignmentCreated(CourseNotificationMessage message) {
        log.info("Course assignment notification received: course={}", message.getCourseCode());
        notify(message, "COURSE_ASSIGNMENT", "Yeni Ödev", "ödev");
    }

    private void notify(CourseNotificationMessage message, String type, String label, String noun) {
        List<UUID> students = message.getEnrolledStudentIds();
        if (students == null || students.isEmpty()) {
            log.warn("Course notification has no enrolled students: course={}", message.getCourseCode());
            return;
        }
        StringBuilder body = new StringBuilder()
                .append(message.getCourseTitle()).append(" (").append(message.getCourseCode()).append(") dersinde yeni bir ")
                .append(noun).append(" paylaşıldı: ").append(message.getContentTitle());
        if (message.getContentDescription() != null && !message.getContentDescription().isBlank()) {
            body.append("\n\n").append(message.getContentDescription());
        }
        String title = "[" + message.getCourseCode() + "] " + label + ": " + message.getContentTitle();
        String link = message.getCourseId() != null ? "/courses/" + message.getCourseId() : null;
        dispatcher.dispatch(NotificationRequest.of(students, NotificationCategory.COURSE, type, title, body.toString(), link, null));
    }
}
