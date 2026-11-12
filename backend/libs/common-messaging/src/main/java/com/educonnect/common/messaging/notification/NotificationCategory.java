package com.educonnect.common.messaging.notification;

public enum NotificationCategory {
    ACCOUNT("Hesap ve güvenlik", true, true),
    COURSE("Dersler", true, true),
    EVENT("Kayıtlı olduğum etkinlikler", true, true),
    MODERATION("Moderasyon kararları", true, true),
    CLUB_MANAGEMENT("Kulüp yönetimi", true, true),
    CLUB_NEWS("Kulüp duyuruları ve etkinlikleri", false, true),
    COMMUNITY("Topluluk etkileşimleri", false, false),
    ACHIEVEMENT("Puan ve rozetler", false, false);

    private final String label;
    private final boolean mandatory;
    private final boolean emailByDefault;

    NotificationCategory(String label, boolean mandatory, boolean emailByDefault) {
        this.label = label;
        this.mandatory = mandatory;
        this.emailByDefault = emailByDefault;
    }

    public String label() {
        return label;
    }

    public boolean mandatory() {
        return mandatory;
    }

    public boolean emailByDefault() {
        return emailByDefault;
    }
}
