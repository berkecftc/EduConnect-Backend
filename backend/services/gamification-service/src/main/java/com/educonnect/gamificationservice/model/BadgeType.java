package com.educonnect.gamificationservice.model;

public enum BadgeType {
    FIRST_STEP("İlk Adım", "İlk puanını kazandın! Yolculuk başlıyor."),
    PROFILE_COMPLETE("Profil Ustası", "Profilini eksiksiz doldurarak Profil Ustası rozetini kazandın!"),
    POINTS_EXPLORER("Puan Kaşifi", "250 puan biriktirerek kaşif unvanını kazandın!"),
    POINTS_MASTER("Puan Ustası", "1000 puan biriktirerek usta unvanını kazandın!"),
    WEEK_WARRIOR("Hafta Savaşçısı", "3 hafta üst üste topluluğa katkı vererek hafta savaşçısı oldun!"),
    FORTNIGHT_WARRIOR("Katkı Ustası", "6 hafta üst üste topluluğa katkı vererek usta oldun!"),
    STREAK_LEGEND("Seri Efsanesi", "12 hafta üst üste topluluğa katkı vererek seri efsanesi oldun!");

    private final String displayName;
    private final String description;

    BadgeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
