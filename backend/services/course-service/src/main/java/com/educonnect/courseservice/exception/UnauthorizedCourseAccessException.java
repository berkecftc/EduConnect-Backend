package com.educonnect.courseservice.exception;

import com.educonnect.common.web.ForbiddenException;

public class UnauthorizedCourseAccessException extends ForbiddenException {
    public UnauthorizedCourseAccessException(String message) {
        super("UNAUTHORIZED_COURSE_ACCESS", message);
    }
}
