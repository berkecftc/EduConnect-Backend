package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.CourseApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public class CourseApplicationResponse {
    private UUID id;
    private UUID courseId;
    private String courseTitle;
    private String courseCode;
    private UUID studentId;
    private String studentName;
    private String studentNumber;
    private String studentEmail;
    private CourseApplicationStatus status;
    private Instant applicationDate;
    private Instant processedDate;
    private String rejectionReason;

    public CourseApplicationResponse() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }

    public CourseApplicationStatus getStatus() { return status; }
    public void setStatus(CourseApplicationStatus status) { this.status = status; }

    public Instant getApplicationDate() { return applicationDate; }
    public void setApplicationDate(Instant applicationDate) { this.applicationDate = applicationDate; }

    public Instant getProcessedDate() { return processedDate; }
    public void setProcessedDate(Instant processedDate) { this.processedDate = processedDate; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
