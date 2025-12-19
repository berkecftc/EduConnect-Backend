package com.educonnect.clubservice.dto.response;

import java.util.UUID;

public class ClubAdminSummaryDto {
    private UUID id;
    private String name;
    private String logoUrl;
    private String presidentName; // Başkanın Adı Soyadı
    private int memberCount;      // Toplam Üye Sayısı

    // Constructor, Getter ve Setter'lar
    public ClubAdminSummaryDto(UUID id, String name, String logoUrl, String presidentName, int memberCount) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
        this.presidentName = presidentName;
        this.memberCount = memberCount;
    }

    // Getter'lar (Lombok @Data varsa gerek yok)
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getLogoUrl() { return logoUrl; }
    public String getPresidentName() { return presidentName; }
    public int getMemberCount() { return memberCount; }
}