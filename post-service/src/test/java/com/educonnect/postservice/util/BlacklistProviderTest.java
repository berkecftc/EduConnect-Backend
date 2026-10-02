package com.educonnect.postservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlacklistProviderTest {

    private final BlacklistProvider provider = new BlacklistProvider();

    @Test
    void matchesWordsAndTheirSuffixedFormsButNotLettersInsideOtherWords() {
        assertThat(provider.containsBadWord("Bu tam bir hakaretti")).isTrue();
        assertThat(provider.containsBadWord("SPAM gönderme")).isTrue();
        assertThat(provider.containsBadWord("Paket kargo ile geldi")).isFalse();
        assertThat(provider.containsBadWord(null)).isFalse();
    }
}
