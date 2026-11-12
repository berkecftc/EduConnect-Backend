package com.educonnect.clubservice.dto.response;

import java.util.UUID;

/**
 * Kulübün danışman akademisyen bilgisini döndüren hafif DTO.
 * Event-service tarafından etkinlik onayı için kullanılır.
 */
public class ClubAdvisorDTO {

    private UUID clubId;
    private UUID advisorId;
    private String clubName;

    public ClubAdvisorDTO() {}

    public ClubAdvisorDTO(UUID clubId, UUID advisorId, String clubName) {
        this.clubId = clubId;
        this.advisorId = advisorId;
        this.clubName = clubName;
    }

    // --- Getter ve Setter ---
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }

    public UUID getAdvisorId() { return advisorId; }
    public void setAdvisorId(UUID advisorId) { this.advisorId = advisorId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
}

