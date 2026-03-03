package com.educonnect.courseservice.exception;

public class DuplicateCourseCodeException extends RuntimeException {
    public DuplicateCourseCodeException(String message) {
        super(message);
    }
}

