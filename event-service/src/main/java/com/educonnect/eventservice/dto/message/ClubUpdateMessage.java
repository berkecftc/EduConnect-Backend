package com.educonnect.eventservice.dto.message; // event-service'te paket adı farklı olabilir

import java.io.Serializable;
import java.util.UUID;

public class ClubUpdateMessage implements Serializable {
    private UUID clubId;
    private String newName;
    private String newLogoUrl; // Eğer logoyu da event tarafında tutuyorsanız

    public ClubUpdateMessage() {}

    public ClubUpdateMessage(UUID clubId, String newName, String newLogoUrl) {
        this.clubId = clubId;
        this.newName = newName;
        this.newLogoUrl = newLogoUrl;
    }

    // Getter ve Setter'lar...
    public UUID getClubId() { return clubId; }
    public void setClubId(UUID clubId) { this.clubId = clubId; }
    public String getNewName() { return newName; }
    public void setNewName(String newName) { this.newName = newName; }
    public String getNewLogoUrl() { return newLogoUrl; }
    public void setNewLogoUrl(String newLogoUrl) { this.newLogoUrl = newLogoUrl; }
}