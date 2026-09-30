package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class ClubProfile {

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ClubCategory category;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "x_url")
    private String xUrl;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    protected ClubProfile() {
    }

    public ClubProfile(ClubCategory category, String contactEmail, String websiteUrl,
                       String instagramUrl, String xUrl, String linkedinUrl) {
        this.category = category;
        this.contactEmail = contactEmail;
        this.websiteUrl = websiteUrl;
        this.instagramUrl = instagramUrl;
        this.xUrl = xUrl;
        this.linkedinUrl = linkedinUrl;
    }

    public static ClubProfile empty() {
        return new ClubProfile();
    }

    public ClubCategory getCategory() { return category; }
    public String getContactEmail() { return contactEmail; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getXUrl() { return xUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
}
