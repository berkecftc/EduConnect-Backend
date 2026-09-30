package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubCategory;
import com.educonnect.clubservice.model.ClubProfile;

public record ClubProfileResponse(ClubCategory category,
                                  String contactEmail,
                                  String websiteUrl,
                                  String instagramUrl,
                                  String xUrl,
                                  String linkedinUrl) {

    public static ClubProfileResponse of(ClubProfile profile) {
        return new ClubProfileResponse(profile.getCategory(), profile.getContactEmail(), profile.getWebsiteUrl(),
                profile.getInstagramUrl(), profile.getXUrl(), profile.getLinkedinUrl());
    }
}
