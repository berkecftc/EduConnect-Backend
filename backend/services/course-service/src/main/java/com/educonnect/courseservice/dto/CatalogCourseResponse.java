package com.educonnect.courseservice.dto;

import com.educonnect.courseservice.model.CatalogCourse;

import java.util.UUID;

public record CatalogCourseResponse(UUID id, String code, String title, int credit, Integer ects) {

    public static CatalogCourseResponse of(CatalogCourse course) {
        return new CatalogCourseResponse(course.getId(), course.getCode(), course.getTitle(), course.getCredit(), course.getEcts());
    }
}
