package com.educonnect.clubservice.model;

import com.educonnect.common.storage.ObjectUrlConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "club_profile_changes")
public class ClubProfileChange {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Embedded
    private ClubProfile profile;

    @Column(name = "logo_url")
    @Convert(converter = ObjectUrlConverter.class)
    private String logoUrl;

    protected ClubProfileChange() {
    }

    private ClubProfileChange(UUID requestId, String about, ClubProfile profile, String logoUrl) {
        this.requestId = requestId;
        this.about = about;
        this.profile = profile;
        this.logoUrl = logoUrl;
    }

    public static ClubProfileChange ofProfile(UUID requestId, String about, ClubProfile profile) {
        return new ClubProfileChange(requestId, about, profile, null);
    }

    public static ClubProfileChange ofLogo(UUID requestId, String logoUrl) {
        return new ClubProfileChange(requestId, null, null, logoUrl);
    }

    public UUID getRequestId() { return requestId; }
    public String getAbout() { return about; }
    public ClubProfile getProfile() { return profile != null ? profile : ClubProfile.empty(); }
    public String getLogoUrl() { return logoUrl; }
}
