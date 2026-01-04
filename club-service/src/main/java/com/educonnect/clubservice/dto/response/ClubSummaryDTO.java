package com.educonnect.clubservice.dto.response;

import java.util.UUID;

public class ClubSummaryDTO {

    private UUID id;
    private String name;
    private String logoUrl;
    private Long memberCount; // Toplam üye sayısı
    private String advisorName; // Danışman hoca ismi
    private UUID advisorId; // Danışman hoca ID

    // JSON dönüşümü için boş constructor
    public ClubSummaryDTO() {}

    // Veritabanı Entity'sinden DTO'ya dönüştürmek için constructor
    public ClubSummaryDTO(UUID id, String name, String logoUrl) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
    }

    // Tam constructor
    public ClubSummaryDTO(UUID id, String name, String logoUrl, Long memberCount, String advisorName, UUID advisorId) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
        this.memberCount = memberCount;
        this.advisorName = advisorName;
        this.advisorId = advisorId;
    }

    // --- Getter ve Setter metotları ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public Long getMemberCount() { return memberCount; }
    public void setMemberCount(Long memberCount) { this.memberCount = memberCount; }
    public String getAdvisorName() { return advisorName; }
    public void setAdvisorName(String advisorName) { this.advisorName = advisorName; }
    public UUID getAdvisorId() { return advisorId; }
    public void setAdvisorId(UUID advisorId) { this.advisorId = advisorId; }
}