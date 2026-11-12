package com.educonnect.postservice.exception;

import com.educonnect.common.web.NotFoundException;

public class PostNotFoundException extends NotFoundException {
    public PostNotFoundException(String message) {
        super("POST_NOT_FOUND", message);
    }
}
