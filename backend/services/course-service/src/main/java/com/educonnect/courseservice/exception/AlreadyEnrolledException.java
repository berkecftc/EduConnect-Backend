package com.educonnect.courseservice.exception;

import com.educonnect.common.web.ConflictException;

public class AlreadyEnrolledException extends ConflictException {
    public AlreadyEnrolledException(String message) {
        super("ALREADY_ENROLLED", message);
    }
}
