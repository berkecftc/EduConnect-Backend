package com.educonnect.courseservice.exception;

import com.educonnect.common.web.ConflictException;

public class DuplicateApplicationException extends ConflictException {
    public DuplicateApplicationException(String message) {
        super("DUPLICATE_APPLICATION", message);
    }
}
