package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubCategory;
import com.educonnect.clubservice.model.ClubProfileChange;

public record ProfileChangeResponse(String about,
                                    ClubCategory category,
                                    String contactEmail,
                                    String websiteUrl,
                                    String instagramUrl,
                                    String xUrl,
                                    String linkedinUrl,
                                    String logoUrl) {

    public static ProfileChangeResponse of(ClubProfileChange change) {
        if (change == null) {
            return null;
        }
        return new ProfileChangeResponse(change.getAbout(), change.getProfile().getCategory(),
                change.getProfile().getContactEmail(), change.getProfile().getWebsiteUrl(),
                change.getProfile().getInstagramUrl(), change.getProfile().getXUrl(),
                change.getProfile().getLinkedinUrl(), change.getLogoUrl());
    }
}
