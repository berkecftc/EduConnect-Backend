package com.educonnect.postservice.exception;

import com.educonnect.common.web.NotFoundException;

public class CommentNotFoundException extends NotFoundException {
    public CommentNotFoundException(String message) {
        super("COMMENT_NOT_FOUND", message);
    }
}
