package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PublisherType;

import java.util.UUID;

public record PostFeedFilter(PostCategory category, PublisherType publisherType, UUID clubId, UUID courseId,
                             Boolean official) {

    public static PostFeedFilter none() {
        return new PostFeedFilter(null, null, null, null, null);
    }
}
