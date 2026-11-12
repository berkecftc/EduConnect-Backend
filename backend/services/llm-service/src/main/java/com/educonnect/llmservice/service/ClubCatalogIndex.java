package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.ClubServiceClient;
import com.educonnect.llmservice.dto.ClubCatalogItem;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ClubCatalogIndex {

    private static final Logger log = LoggerFactory.getLogger(ClubCatalogIndex.class);

    private static final Map<String, String> CATEGORIES = Map.of(
            "ACADEMIC", "Akademik",
            "SCIENCE_TECHNOLOGY", "Bilim ve teknoloji",
            "ARTS_CULTURE", "Sanat ve kültür",
            "SPORTS", "Spor",
            "SOCIAL_RESPONSIBILITY", "Sosyal sorumluluk",
            "HOBBY", "Hobi",
            "CAREER", "Kariyer",
            "OTHER", "Diğer");

    public record ClubInfo(String clubName, String category, String description) {
    }

    private final EmbeddingModel embeddingModel;
    private final ClubServiceClient clubServiceClient;
    private final Path storeFile;
    private final boolean enabled;
    private final Duration retryInterval;
    private final int maxAttempts;
    private final Duration debounce;
    private final AtomicReference<SimpleVectorStore> store;
    private final AtomicBoolean queued = new AtomicBoolean();
    private final AtomicInteger failures = new AtomicInteger();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "club-catalog-refresh");
        thread.setDaemon(true);
        return thread;
    });

    public ClubCatalogIndex(EmbeddingModel embeddingModel,
                            ClubServiceClient clubServiceClient,
                            @Value("${vector.store.path:data/vector-store.json}") String storePath,
                            @Value("${club.ingestion.enabled:true}") boolean enabled,
                            @Value("${club.ingestion.retry-interval:PT20S}") Duration retryInterval,
                            @Value("${club.ingestion.max-attempts:15}") int maxAttempts,
                            @Value("${club.ingestion.debounce:PT5S}") Duration debounce) {
        this.embeddingModel = embeddingModel;
        this.clubServiceClient = clubServiceClient;
        this.storeFile = Path.of(storePath);
        this.enabled = enabled;
        this.retryInterval = retryInterval;
        this.maxAttempts = maxAttempts;
        this.debounce = debounce;
        this.store = new AtomicReference<>(load());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        schedule(Duration.ZERO);
    }

    public void requestRefresh() {
        schedule(debounce);
    }

    public List<ClubInfo> search(String query) {
        return store.get().similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(5)
                        .similarityThreshold(0.50)
                        .build())
                .stream()
                .map(ClubCatalogIndex::toClubInfo)
                .toList();
    }

    boolean refresh() {
        List<ClubCatalogItem> catalog;
        try {
            catalog = clubServiceClient.getClubCatalog();
        } catch (RuntimeException e) {
            log.warn("Club catalog could not be loaded from club-service: {}", e.getMessage());
            return false;
        }
        SimpleVectorStore next = SimpleVectorStore.builder(embeddingModel).build();
        try {
            if (!catalog.isEmpty()) {
                next.add(catalog.stream().map(ClubCatalogIndex::toDocument).toList());
            }
            save(next);
        } catch (RuntimeException e) {
            log.warn("Club catalog could not be embedded; is the embedding model available? {}", e.getMessage());
            return false;
        }
        store.set(next);
        log.info("Club catalog refreshed: {} active clubs", catalog.size());
        return true;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void schedule(Duration delay) {
        if (!enabled || !queued.compareAndSet(false, true)) {
            return;
        }
        executor.schedule(this::run, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void run() {
        queued.set(false);
        if (refresh()) {
            failures.set(0);
            return;
        }
        int failed = failures.incrementAndGet();
        if (failed >= maxAttempts) {
            log.warn("Club catalog refresh gave up after {} attempts; it will retry on the next club change.", failed);
            failures.set(0);
            return;
        }
        schedule(retryInterval);
    }

    private SimpleVectorStore load() {
        SimpleVectorStore loaded = SimpleVectorStore.builder(embeddingModel).build();
        File file = storeFile.toFile();
        if (file.isFile()) {
            try {
                loaded.load(file);
            } catch (RuntimeException e) {
                log.warn("Stored club catalog could not be read, starting empty: {}", e.getMessage());
                return SimpleVectorStore.builder(embeddingModel).build();
            }
        }
        return loaded;
    }

    private void save(SimpleVectorStore next) {
        try {
            Path parent = storeFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = storeFile.resolveSibling(storeFile.getFileName() + ".tmp");
            next.save(temp.toFile());
            try {
                Files.move(temp, storeFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, storeFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("Club catalog could not be written to {}: {}", storeFile, e.getMessage());
        }
    }

    private static Document toDocument(ClubCatalogItem club) {
        String category = CATEGORIES.getOrDefault(Objects.requireNonNullElse(club.category(), ""), "Belirtilmedi");
        String about = club.about() == null || club.about().isBlank() ? "Açıklama mevcut değil." : club.about().strip();
        return Document.builder()
                .id(club.id())
                .text("Kulüp Adı: " + club.name() + "\nKategori: " + category + "\nAçıklama: " + about)
                .metadata(Map.of("clubId", club.id(), "name", club.name(), "category", category, "about", about))
                .build();
    }

    private static ClubInfo toClubInfo(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new ClubInfo(
                Objects.toString(metadata.get("name"), "İsimsiz Kulüp"),
                Objects.toString(metadata.get("category"), "Belirtilmedi"),
                Objects.toString(metadata.get("about"), "Açıklama mevcut değil."));
    }
}
