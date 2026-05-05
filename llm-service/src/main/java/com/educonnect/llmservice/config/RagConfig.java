package com.educonnect.llmservice.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.io.File;

@Configuration
public class RagConfig {

    @Value("${vector.store.path:data/vector-store.json}")
    private String vectorStorePath;

    @Value("${club.ingestion.force:false}")
    private boolean forceIngestion;

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();

        File vectorStoreFile = new File(vectorStorePath);
        File parentDir = vectorStoreFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        if (vectorStoreFile.exists() && !forceIngestion) {
            simpleVectorStore.load(vectorStoreFile);
        }

        return simpleVectorStore;
    }
}