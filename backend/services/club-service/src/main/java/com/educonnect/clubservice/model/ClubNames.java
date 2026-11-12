package com.educonnect.clubservice.model;

import java.util.Locale;
import java.util.Map;

public final class ClubNames {

    private static final Map<Character, Character> TURKISH_FOLDING = Map.ofEntries(
            Map.entry('İ', 'i'), Map.entry('I', 'i'), Map.entry('ı', 'i'),
            Map.entry('Ğ', 'g'), Map.entry('ğ', 'g'),
            Map.entry('Ü', 'u'), Map.entry('ü', 'u'),
            Map.entry('Ş', 's'), Map.entry('ş', 's'),
            Map.entry('Ö', 'o'), Map.entry('ö', 'o'),
            Map.entry('Ç', 'c'), Map.entry('ç', 'c'),
            Map.entry('Â', 'a'), Map.entry('â', 'a'),
            Map.entry('Î', 'i'), Map.entry('î', 'i'),
            Map.entry('Û', 'u'), Map.entry('û', 'u'));

    private ClubNames() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder folded = new StringBuilder(name.length());
        for (char c : name.strip().toCharArray()) {
            folded.append(TURKISH_FOLDING.getOrDefault(c, c));
        }
        return folded.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
