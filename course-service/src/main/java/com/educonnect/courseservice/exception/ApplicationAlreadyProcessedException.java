package com.educonnect.courseservice.exception;

public class ApplicationAlreadyProcessedException extends RuntimeException {
    public ApplicationAlreadyProcessedException(String message) {
        super(message);
    }
}

