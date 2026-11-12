package com.educonnect.common.messaging.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneId;

public final class TurkishDates {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", TURKISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", TURKISH);

    private TurkishDates() {
    }

    public static String format(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    public static String format(Instant value) {
        return value == null ? "" : DATE_TIME.format(value.atZone(ZONE));
    }

    public static String format(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }
}
