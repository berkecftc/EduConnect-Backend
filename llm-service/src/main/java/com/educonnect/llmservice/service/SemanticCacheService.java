package com.educonnect.llmservice.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SemanticCacheService {

    private final VectorStore vectorStore;
    private static final double SIMILARITY_THRESHOLD = 0.90;

    public SemanticCacheService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Mesajı vektör veritabanında arar ve eşleşme varsa önbelleklenen cevabı döner.
     */
    public String getCachedResponse(String userMessage) {
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userMessage)
                        .topK(1)
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .filterExpression("type == 'semantic_cache'")
                        .build()
        );

        if (similarDocuments != null && !similarDocuments.isEmpty()) {
            return (String) similarDocuments.getFirst().getMetadata().get("cachedResponse");
        }
        return null;
    }

    /**
     * Yeni LLM cevabını vektör veritabanına cacheler.
     */
    public void putCache(String userMessage, String llmResponse) {
        Document cacheDoc = new Document(userMessage, Map.of(
                "type", "semantic_cache",
                "cachedResponse", llmResponse
        ));
        vectorStore.add(List.of(cacheDoc));
    }
}

