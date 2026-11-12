package com.educonnect.courseservice.exception;

import com.educonnect.common.web.NotFoundException;

public class CourseNotFoundException extends NotFoundException {
    public CourseNotFoundException(String message) {
        super("COURSE_NOT_FOUND", message);
    }
}
