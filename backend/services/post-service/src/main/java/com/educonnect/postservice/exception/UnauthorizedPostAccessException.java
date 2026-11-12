package com.educonnect.postservice.exception;

import com.educonnect.common.web.ForbiddenException;

public class UnauthorizedPostAccessException extends ForbiddenException {
    public UnauthorizedPostAccessException(String message) {
        super("POST_ACCESS_DENIED", message);
    }
}
