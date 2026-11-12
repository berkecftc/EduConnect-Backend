package com.educonnect.clubservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ClubPosition {

    PRESIDENT("ROLE_CLUB_OFFICIAL", "Kulüp Başkanı", true, true, 1),
    VICE_PRESIDENT("ROLE_VICE_PRESIDENT", "Başkan Yardımcısı", true, true, 1),
    GENERAL_SECRETARY("ROLE_SECRETARY", "Genel Sekreter", true, true, 1),
    TREASURER("ROLE_TREASURER", "Sayman", true, true, 1),
    BOARD_MEMBER("ROLE_BOARD_MEMBER", "Yönetim Kurulu Üyesi", true, true, 7),
    AUDITOR("ROLE_AUDITOR", "Denetim Kurulu Üyesi", true, false, 3),
    EVENT_COORDINATOR("ROLE_EVENT_COORDINATOR", "Etkinlik Koordinatörü", true, false, Integer.MAX_VALUE),
    COMMUNICATIONS_OFFICER("ROLE_COMMUNICATIONS_OFFICER", "İletişim ve Sosyal Medya Sorumlusu", true, false, Integer.MAX_VALUE),
    MEMBERSHIP_OFFICER("ROLE_MEMBERSHIP_OFFICER", "Üyelik Sorumlusu", true, false, Integer.MAX_VALUE),
    SPONSORSHIP_OFFICER("ROLE_SPONSORSHIP_OFFICER", "Sponsorluk ve Dış İlişkiler Sorumlusu", true, false, Integer.MAX_VALUE),
    MEMBER("ROLE_MEMBER", "Üye", false, false, Integer.MAX_VALUE);

    private static volatile boolean legacyApiNames = true;

    private final String legacyName;
    private final String displayName;
    private final boolean management;
    private final boolean board;
    private final int maxActiveHolders;

    ClubPosition(String legacyName, String displayName, boolean management, boolean board, int maxActiveHolders) {
        this.legacyName = legacyName;
        this.displayName = displayName;
        this.management = management;
        this.board = board;
        this.maxActiveHolders = maxActiveHolders;
    }

    public boolean isManagement() {
        return management;
    }

    public boolean isBoard() {
        return board;
    }

    public int maxActiveHolders() {
        return maxActiveHolders;
    }

    public String legacyName() {
        return legacyName;
    }

    public String displayName() {
        return displayName;
    }

    @JsonValue
    public String apiName() {
        return legacyApiNames ? legacyName : name();
    }

    @JsonCreator
    public static ClubPosition fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(position -> position.name().equalsIgnoreCase(normalized)
                        || position.legacyName.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown club position: " + value));
    }

    public static void useLegacyApiNames(boolean enabled) {
        legacyApiNames = enabled;
    }
}
