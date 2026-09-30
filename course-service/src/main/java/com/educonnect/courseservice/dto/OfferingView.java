package com.educonnect.courseservice.dto;

import java.util.UUID;

public interface OfferingView {

    void setTermId(UUID termId);

    void setTermLabel(String termLabel);

    void setSection(String section);

    void setCatalogCourseId(UUID catalogCourseId);

    void setEcts(Integer ects);
}
