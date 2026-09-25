package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.ClubServiceClient;
import com.educonnect.llmservice.dto.ClubResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ClubIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ClubIngestionService.class);
    private final VectorStore vectorStore;
    private final ClubServiceClient clubServiceClient;

    @Value("${vector.store.path:data/vector-store.json}")
    private String vectorStorePath;

    @Value("${club.ingestion.enabled:true}")
    private boolean ingestionEnabled;

    @Value("${club.ingestion.force:false}")
    private boolean forceIngestion;

    @Value("${club.ingestion.retry-interval:PT20S}")
    private Duration retryInterval;

    @Value("${club.ingestion.max-attempts:15}")
    private int maxAttempts;

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "club-ingestion-retry");
        thread.setDaemon(true);
        return thread;
    });

    public ClubIngestionService(@Qualifier("clubVectorStore") VectorStore vectorStore, ClubServiceClient clubServiceClient) {
        this.vectorStore = vectorStore;
        this.clubServiceClient = clubServiceClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestClubsToVectorStore() {
        runAttempt(1);
    }

    @PreDestroy
    void shutdown() {
        retryExecutor.shutdownNow();
    }

    private void runAttempt(int attempt) {
        if (attemptIngestion()) {
            return;
        }
        if (attempt >= maxAttempts) {
            log.warn("Kulüp ETL {} denemede tamamlanamadı, vazgeçildi.", attempt);
            return;
        }
        log.info("Kulüp ETL {} sn sonra yeniden denenecek ({}/{}).", retryInterval.toSeconds(), attempt + 1, maxAttempts);
        retryExecutor.schedule(() -> runAttempt(attempt + 1), retryInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    boolean attemptIngestion() {
        if (!ingestionEnabled) {
            log.info("Kulüp ETL devre dışı. club.ingestion.enabled=false");
            return true;
        }

        File storeFile = new File(vectorStorePath);
        File parentDir = storeFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        if (storeFile.exists() && !forceIngestion) {
            log.info("Kulüp vektör veritabanı zaten dolu, ETL atlanıyor.");
            return true;
        }
        if (storeFile.exists() && forceIngestion) {
            if (!storeFile.delete()) {
                log.warn("Eski vektör dosyası silinemedi: {}", storeFile.getAbsolutePath());
            }
        }

        log.info("club-service üzerinden kulüp verileri çekiliyor...");

        List<ClubResponse> clubs;
        try {
            clubs = fetchClubsFromClubService();
        } catch (RuntimeException ex) {
            log.warn("Kulüp verileri club-service'ten alınamadı: {}", ex.getMessage());
            return false;
        }
        if (clubs.isEmpty()) {
            log.warn("Kulüp verisi bulunamadı. Vektör veritabanı güncellenmedi.");
            return true;
        }

        log.info("Veriler Yapay Zeka Dokümanlarına (Document) dönüştürülüyor...");

        List<Document> aiDocuments = clubs.stream()
                .map(club -> {
                    // LLM'in okuyacağı semantik (anlamsal) metin
                    String semantikMetin = String.format(
                            "Kulüp Adı: %s\nKategori: %s\nAçıklama: %s\nToplantı Günü: %s",
                            club.name(), club.category(), club.description(), club.meetingDay()
                    );

                    // Metadata: Arama sonuçlarında ID'ye ulaşabilmek için (Kritik detay)
                    Map<String, Object> metadata = Map.of("clubId", club.id());

                    return new Document(semantikMetin, metadata);
                })
                .toList();

        log.info("Dokümanlar Vektör Veritabanına kaydediliyor...");
        try {
            vectorStore.accept(aiDocuments);
        } catch (RuntimeException ex) {
            log.warn("Vektörleştirme başarısız. Embedding modeli kurulu olmayabilir. " +
                    "Ollama'da ilgili modeli indirip yeniden başlatın.", ex);
            return false;
        }

        if (vectorStore instanceof SimpleVectorStore simpleStore) {
            simpleStore.save(storeFile);
        }
        log.info("Kulüp RAG entegrasyonu tamamlandı!");
        return true;
    }

    private List<ClubResponse> fetchClubsFromClubService() {
        return clubServiceClient.getClubCatalog().stream()
                .map(club -> new ClubResponse(
                        club.id(),
                        club.name(),
                        "Belirtilmedi",
                        club.about() != null ? club.about() : "",
                        "Belirtilmedi"
                ))
                .toList();
    }
}
