package com.educonnect.postservice.util;

import org.springframework.stereotype.Component;

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
        String lowerText = text.toLowerCase();
        return BLACKLIST.stream().anyMatch(lowerText::contains);
    }

    /**
     * Blacklist kelimelerini döndürür (read-only).
     */
    public Set<String> getBlacklistWords() {
        return BLACKLIST;
    }
}

