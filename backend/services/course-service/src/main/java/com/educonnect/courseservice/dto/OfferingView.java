package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.CourseStatus;

import java.util.UUID;

public interface OfferingView {

    void setTermId(UUID termId);

    void setTermLabel(String termLabel);

    void setSection(String section);

    void setCatalogCourseId(UUID catalogCourseId);

    void setEcts(Integer ects);

    void setStatus(CourseStatus status);
}
