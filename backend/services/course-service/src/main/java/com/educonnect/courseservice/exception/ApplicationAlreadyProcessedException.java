package com.educonnect.courseservice.exception;

import com.educonnect.common.web.ConflictException;

public class ApplicationAlreadyProcessedException extends ConflictException {
    public ApplicationAlreadyProcessedException(String message) {
        super("APPLICATION_ALREADY_PROCESSED", message);
    }
}
