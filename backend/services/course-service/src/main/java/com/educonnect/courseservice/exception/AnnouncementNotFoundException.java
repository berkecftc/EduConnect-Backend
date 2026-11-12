package com.educonnect.courseservice.exception;

import com.educonnect.common.web.NotFoundException;

public class AnnouncementNotFoundException extends NotFoundException {
    public AnnouncementNotFoundException(String message) {
        super("ANNOUNCEMENT_NOT_FOUND", message);
    }
}
