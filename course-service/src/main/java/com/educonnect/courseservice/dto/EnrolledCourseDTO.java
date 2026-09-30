package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.CourseStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class EnrolledCourseDTO implements OfferingView {
    private UUID id;
    private String title;
    private String code;
    private String description;
    private int credit;
    private String semester;
    private UUID termId;
    private String termLabel;
    private String section;
    private UUID catalogCourseId;
    private Integer ects;
    private CourseStatus status;
    private String imageUrl;
    private UUID instructorId;
    private String instructorName;
    private LocalDateTime enrollmentDate;

    public EnrolledCourseDTO() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCredit() { return credit; }
    public void setCredit(int credit) { this.credit = credit; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public UUID getTermId() { return termId; }
    public void setTermId(UUID termId) { this.termId = termId; }
    public String getTermLabel() { return termLabel; }
    public void setTermLabel(String termLabel) { this.termLabel = termLabel; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public UUID getCatalogCourseId() { return catalogCourseId; }
    public void setCatalogCourseId(UUID catalogCourseId) { this.catalogCourseId = catalogCourseId; }
    public Integer getEcts() { return ects; }
    public void setEcts(Integer ects) { this.ects = ects; }
    public CourseStatus getStatus() { return status; }
    public void setStatus(CourseStatus status) { this.status = status; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public UUID getInstructorId() { return instructorId; }
    public void setInstructorId(UUID instructorId) { this.instructorId = instructorId; }

    public String getInstructorName() { return instructorName; }
    public void setInstructorName(String instructorName) { this.instructorName = instructorName; }

    public LocalDateTime getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(LocalDateTime enrollmentDate) { this.enrollmentDate = enrollmentDate; }
}
