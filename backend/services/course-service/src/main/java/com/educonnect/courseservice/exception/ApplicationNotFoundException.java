package com.educonnect.courseservice.exception;

import com.educonnect.common.web.NotFoundException;

public class ApplicationNotFoundException extends NotFoundException {
    public ApplicationNotFoundException(String message) {
        super("APPLICATION_NOT_FOUND", message);
    }
}
