package com.educonnect.common.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidUploadException extends ResponseStatusException {

    public InvalidUploadException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }

    @Override
    public String getMessage() {
        return getReason();
    }
}
