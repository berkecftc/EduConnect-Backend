package com.educonnect.courseservice.exception;

import com.educonnect.common.web.BadRequestException;

public class CourseCapacityFullException extends BadRequestException {
    public CourseCapacityFullException(String message) {
        super("COURSE_CAPACITY_FULL", message);
    }
}
