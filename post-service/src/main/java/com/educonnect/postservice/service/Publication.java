package com.educonnect.postservice.service;

import com.educonnect.postservice.model.PublisherType;

import java.util.UUID;

record Publication(PublisherType type, UUID clubId, UUID courseId, String name, boolean needsApproval) {

    static Publication forum() {
        return new Publication(PublisherType.STUDENT, null, null, null, false);
    }
}
