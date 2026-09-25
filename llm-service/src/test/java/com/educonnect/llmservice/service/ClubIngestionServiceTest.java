package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.ClubServiceClient;
import com.educonnect.llmservice.dto.ClubCatalogItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ClubServiceClient clubServiceClient;

    @TempDir
    Path tempDir;

    private ClubIngestionService service;

    @BeforeEach
    void setUp() {
        service = new ClubIngestionService(vectorStore, clubServiceClient);
        ReflectionTestUtils.setField(service, "vectorStorePath", tempDir.resolve("vector-store.json").toString());
        ReflectionTestUtils.setField(service, "ingestionEnabled", true);
        ReflectionTestUtils.setField(service, "forceIngestion", false);
        ReflectionTestUtils.setField(service, "retryInterval", Duration.ofMillis(20));
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void attemptIngestion_whenCatalogUnavailable_shouldReportFailureWithoutTouchingStore() {
        when(clubServiceClient.getClubCatalog()).thenThrow(new IllegalStateException("No instances available for auth-services"));

        boolean done = service.attemptIngestion();

        assertThat(done).isFalse();
        verify(vectorStore, never()).accept(anyList());
    }

    @Test
    void ingestClubsToVectorStore_whenCatalogBecomesAvailable_shouldRetryAndIngest() {
        when(clubServiceClient.getClubCatalog())
                .thenThrow(new IllegalStateException("No instances available for auth-services"))
                .thenReturn(List.of(new ClubCatalogItem("c1", "Yazılım Kulübü", "Yazılım geliştirme")));

        service.ingestClubsToVectorStore();

        verify(vectorStore, timeout(2000)).accept(anyList());
        verify(clubServiceClient, after(200).times(2)).getClubCatalog();
    }

    @Test
    void ingestClubsToVectorStore_whenCatalogNeverAvailable_shouldStopAfterMaxAttempts() {
        when(clubServiceClient.getClubCatalog()).thenThrow(new IllegalStateException("No instances available for auth-services"));

        service.ingestClubsToVectorStore();

        verify(clubServiceClient, timeout(2000).times(3)).getClubCatalog();
        verify(clubServiceClient, after(300).times(3)).getClubCatalog();
        verify(vectorStore, never()).accept(anyList());
    }
}
