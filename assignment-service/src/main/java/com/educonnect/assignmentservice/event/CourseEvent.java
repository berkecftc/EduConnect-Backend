package com.educonnect.assignmentservice.event;
import java.io.Serializable;
import java.util.UUID;

// RabbitMQ'dan gelen mesajı karşılamak için
public class CourseEvent implements Serializable {
    private UUID courseId;
    private String title;
    private String code;
    private String message;

    public CourseEvent() {}

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}