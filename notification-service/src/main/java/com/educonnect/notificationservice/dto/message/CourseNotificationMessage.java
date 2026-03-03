package com.educonnect.notificationservice.dto.message;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Course-service veya assignment-service'den RabbitMQ ile gelen bildirim mesajı.
 * Duyuru veya ödev oluşturulduğunda tetiklenir.
 */
public class CourseNotificationMessage implements Serializable {
    private UUID courseId;
    private String courseTitle;
    private String courseCode;
    private String notificationType; // "ANNOUNCEMENT" veya "ASSIGNMENT"
    private String contentTitle;
    private String contentDescription;
    private List<UUID> enrolledStudentIds;

    public CourseNotificationMessage() {}

    // Getters and Setters
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

    public String getContentTitle() { return contentTitle; }
    public void setContentTitle(String contentTitle) { this.contentTitle = contentTitle; }

    public String getContentDescription() { return contentDescription; }
    public void setContentDescription(String contentDescription) { this.contentDescription = contentDescription; }

    public List<UUID> getEnrolledStudentIds() { return enrolledStudentIds; }
    public void setEnrolledStudentIds(List<UUID> enrolledStudentIds) { this.enrolledStudentIds = enrolledStudentIds; }
}

