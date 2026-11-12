package com.educonnect.courseservice.exception;

import com.educonnect.common.web.ConflictException;

public class DuplicateCourseCodeException extends ConflictException {
    public DuplicateCourseCodeException(String message) {
        super("DUPLICATE_COURSE_CODE", message);
    }
}
