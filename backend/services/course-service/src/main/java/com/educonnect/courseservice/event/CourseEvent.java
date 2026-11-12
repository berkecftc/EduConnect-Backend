package com.educonnect.courseservice.event;
import java.io.Serializable;
import java.util.UUID;

public class CourseEvent implements Serializable {
    private UUID courseId;
    private String title;
    private String code;
    private String message;

    public CourseEvent() {}
    public CourseEvent(UUID courseId, String title, String code, String message) {
        this.courseId = courseId;
        this.title = title;
        this.code = code;
        this.message = message;
    }

    // Getter & Setter
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}