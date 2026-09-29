package com.educonnect.courseservice.exception;

import com.educonnect.common.web.NotFoundException;

public class EnrollmentNotFoundException extends NotFoundException {
    public EnrollmentNotFoundException(String message) {
        super("ENROLLMENT_NOT_FOUND", message);
    }
}
