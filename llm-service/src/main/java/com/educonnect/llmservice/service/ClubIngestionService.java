package com.educonnect.llmservice.service;

import com.educonnect.llmservice.dto.ClubResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.util.List;
import java.util.Map;

@Service
public class ClubIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ClubIngestionService.class);
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Value("${vector.store.path:data/vector-store.json}")
    private String vectorStorePath;

    @Value("${club.ingestion.enabled:true}")
    private boolean ingestionEnabled;

    @Value("${club.ingestion.force:false}")
    private boolean forceIngestion;

    public ClubIngestionService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ingestClubsToVectorStore() {
        if (!ingestionEnabled) {
            log.info("Kulüp ETL devre dışı. club.ingestion.enabled=false");
            return;
        }

        File storeFile = new File(vectorStorePath);
        File parentDir = storeFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        if (storeFile.exists() && !forceIngestion) {
            log.info("Kulüp vektör veritabanı zaten dolu, ETL atlanıyor.");
            return;
        }
        if (storeFile.exists() && forceIngestion) {
            if (!storeFile.delete()) {
                log.warn("Eski vektör dosyası silinemedi: {}", storeFile.getAbsolutePath());
            }
        }

        log.info("PostgreSQL üzerinden kulüp verileri çekiliyor...");

        List<ClubResponse> clubs = fetchClubsFromDatabase();
        if (clubs.isEmpty()) {
            log.warn("Kulüp verisi bulunamadı. Vektör veritabanı güncellenmedi.");
            return;
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
            return;
        }

        if (vectorStore instanceof SimpleVectorStore simpleStore) {
            simpleStore.save(storeFile);
        }
        log.info("Kulüp RAG entegrasyonu tamamlandı!");
    }

    private List<ClubResponse> fetchClubsFromDatabase() {
        String sql = """
            SELECT id::text AS id,
                   name,
                   COALESCE(about, '') AS about
            FROM clubs
            ORDER BY name
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ClubResponse(
                rs.getString("id"),
                rs.getString("name"),
                "Belirtilmedi",
                rs.getString("about"),
                "Belirtilmedi"
        ));
    }
}

