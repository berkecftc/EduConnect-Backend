package com.educonnect.courseservice.exception;

public class UnauthorizedCourseAccessException extends RuntimeException {
    public UnauthorizedCourseAccessException(String message) {
        super(message);
    }
}

