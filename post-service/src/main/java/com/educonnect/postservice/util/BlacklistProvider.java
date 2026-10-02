package com.educonnect.postservice.util;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Kötü kelime blacklist'i sağlayan ortak util sınıfı.
 * Hem post moderasyonunda hem yorum moderasyonunda kullanılır.
 *
 * Gerçek uygulamada bu bir DB tablosu, harici API veya AI servisi olabilir.
 * Şu an mock olarak statik bir Set kullanılmaktadır.
 */
@Component
public class BlacklistProvider {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private static final Set<String> BLACKLIST = Set.of(
            "küfür", "hakaret", "spam", "reklam", "argo",
            "nefret", "şiddet", "taciz", "dolandırıcılık"
    );

    /**
     * Verilen metni blacklist'teki kelimelerle tarar.
     * Eşleşme varsa true döner.
     */
    public boolean containsBadWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String[] tokens = text.toLowerCase(TURKISH).split("[^\\p{L}\\p{N}]+");
        return Arrays.stream(tokens).anyMatch(token -> BLACKLIST.stream().anyMatch(token::startsWith));
    }

    /**
     * Blacklist kelimelerini döndürür (read-only).
     */
    public Set<String> getBlacklistWords() {
        return BLACKLIST;
    }
}
