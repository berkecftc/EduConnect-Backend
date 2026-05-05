package com.educonnect.llmservice.service;

import com.educonnect.llmservice.dto.ClubResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.util.List;
import java.util.Map;

@Service
public class ClubIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ClubIngestionService.class);
    private final VectorStore vectorStore;

    @Value("${vector.store.path:data/vector-store.json}")
    private String vectorStorePath;

    @Value("${club.ingestion.enabled:true}")
    private boolean ingestionEnabled;

    public ClubIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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
        if (storeFile.exists()) {
            log.info("Kulüp vektör veritabanı zaten dolu, ETL atlanıyor.");
            return;
        }

        log.info("Veritabanından (veya JSON'dan) kulüp verileri çekiliyor...");

        // Simüle edilmiş veritabanı verisi (Bunu OpenFeign ile club-service'den de çekebilirsin)
        List<ClubResponse> clubs = List.of(
                new ClubResponse("C-101", "Yapay Zeka ve Siber Güvenlik Kulübü", "Teknoloji",
                        "Makine öğrenmesi, LLM'ler ve ağ güvenliği üzerine projeler geliştiren, siber zorbalık tespiti gibi asenkron mimariler kuran mühendislik topluluğudur.", "Çarşamba"),
                new ClubResponse("C-102", "Doğa Sporları ve Kampçılık Kulübü", "Sosyal",
                        "Hafta sonları trekking, dağcılık ve kamp etkinlikleri düzenleyen, doğayı seven öğrencilerin buluşma noktasıdır.", "Cuma"),
                new ClubResponse("C-103", "Kariyer ve Girişimcilik Kulübü", "İş Dünyası",
                        "Sektör liderlerini üniversitemizde ağırlayan, öğrencileri mülakatlara (Doğuş Teknoloji, Jotform vb.) hazırlayan kariyer odaklı bir kulüptür.", "Salı")
        );

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
}