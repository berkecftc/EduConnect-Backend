package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.ClubServiceClient;
import com.educonnect.llmservice.dto.ClubCatalogItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClubCatalogIndexTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final ClubServiceClient clubServiceClient = mock(ClubServiceClient.class);

    @TempDir
    Path tempDir;

    private ClubCatalogIndex index;

    @BeforeEach
    void setUp() {
        when(embeddingModel.embed(anyString())).thenAnswer(call -> vector(call.getArgument(0)));
        when(embeddingModel.embed(any(Document.class))).thenAnswer(call -> vector(call.<Document>getArgument(0).getText()));
        when(embeddingModel.dimensions()).thenReturn(2);
        index = newIndex();
    }

    @AfterEach
    void tearDown() {
        index.shutdown();
    }

    @Test
    void refreshReplacesTheCatalogInsteadOfAppendingAndUsesRealCategories() {
        String software = UUID.randomUUID().toString();
        String music = UUID.randomUUID().toString();
        when(clubServiceClient.getClubCatalog()).thenReturn(List.of(
                new ClubCatalogItem(software, "Yazılım Kulübü", "Yazılım geliştirme atölyeleri", "SCIENCE_TECHNOLOGY"),
                new ClubCatalogItem(music, "Müzik Kulübü", null, null)));

        assertThat(index.refresh()).isTrue();
        assertThat(index.search("yazılım")).singleElement()
                .isEqualTo(new ClubCatalogIndex.ClubInfo("Yazılım Kulübü", "Bilim ve teknoloji", "Yazılım geliştirme atölyeleri"));
        assertThat(index.search("müzik")).singleElement()
                .isEqualTo(new ClubCatalogIndex.ClubInfo("Müzik Kulübü", "Belirtilmedi", "Açıklama mevcut değil."));

        when(clubServiceClient.getClubCatalog()).thenReturn(List.of(
                new ClubCatalogItem(music, "Müzik Kulübü", "Koro ve orkestra", "ARTS_CULTURE")));
        assertThat(index.refresh()).isTrue();

        assertThat(index.search("yazılım")).isEmpty();
        assertThat(index.search("müzik")).singleElement().extracting(ClubCatalogIndex.ClubInfo::category).isEqualTo("Sanat ve kültür");
    }

    @Test
    void aFailedRefreshKeepsTheLastCatalogAndTheStoredFileSurvivesARestart() {
        when(clubServiceClient.getClubCatalog()).thenReturn(List.of(
                new ClubCatalogItem(UUID.randomUUID().toString(), "Yazılım Kulübü", "Kod", "ACADEMIC")));
        assertThat(index.refresh()).isTrue();

        when(clubServiceClient.getClubCatalog()).thenThrow(new IllegalStateException("club-service down"));
        assertThat(index.refresh()).isFalse();
        assertThat(index.search("yazılım")).hasSize(1);

        ClubCatalogIndex restarted = newIndex();
        try {
            assertThat(restarted.search("yazılım")).singleElement().extracting(ClubCatalogIndex.ClubInfo::category).isEqualTo("Akademik");
        } finally {
            restarted.shutdown();
        }
    }

    private ClubCatalogIndex newIndex() {
        return new ClubCatalogIndex(embeddingModel, clubServiceClient, tempDir.resolve("store/vector-store.json").toString(),
                true, Duration.ofMillis(10), 2, Duration.ofMillis(10));
    }

    private static float[] vector(String text) {
        String lower = text.toLowerCase(Locale.forLanguageTag("tr"));
        return new float[]{lower.contains("yazılım") ? 1f : 0f, lower.contains("müzik") ? 1f : 0f};
    }
}
