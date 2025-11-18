package com.educonnect.clubservice.dto.response;

import java.util.UUID;

public class ClubSummaryDTO {

    private UUID id;
    private String name;
    private String logoUrl;

    // JSON dönüşümü için boş constructor
    public ClubSummaryDTO() {}

    // Veritabanı Entity'sinden DTO'ya dönüştürmek için constructor
    public ClubSummaryDTO(UUID id, String name, String logoUrl) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
    }

    // --- Getter ve Setter metotları ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
}