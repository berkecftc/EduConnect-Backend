package com.educonnect.courseservice.exception;

public class CourseCapacityFullException extends RuntimeException {
    public CourseCapacityFullException(String message) {
        super(message);
    }
}

