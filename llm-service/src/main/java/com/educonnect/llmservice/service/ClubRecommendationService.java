package com.educonnect.llmservice.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClubRecommendationService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ClubRecommendationService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    public String recommendClub(String studentMessage) {

        // 1. Öğrencinin mesajını vektöre çevir ve en benzer 2 kulübü getir
        List<Document> similarClubs = vectorStore.similaritySearch(
                SearchRequest.builder().query(studentMessage).topK(2).build()
        );

        // 2. Gelen kulüp verilerini metin olarak birleştir
        String context = similarClubs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 3. Prompt Guardrails (Demir Yumruk Kuralları)
        String systemRules = String.format("""
            Sen EduConnect platformunun profesyonel öğrenci kulübü danışmanısın.
            Aşağıdaki KULÜP VERİTABANI kayıtlarını kullanarak öğrenciye en uygun kulübü tavsiye et.
            
            KULÜP VERİTABANI:
            %s
            
            KESİN KURALLAR (BUNLARA UYMAZSAN SİSTEM ÇÖKER):
            1. Yalnızca veritabanındaki kulüpleri tavsiye et.
            2. ASLA İngilizce veya yarı İngilizce kelimeler kullanma (örn: close, cyber, security). Tamamen saf Türkçe konuş.
            3. Tavsiye ettiğin her kulübün TOPLANTI GÜNÜNÜ kesinlikle cümleye ekle (Örn: 'Toplantıları Çarşamba günüdür.').
            """, context);

        // 4. LLM Çağrısı
        return chatClient.prompt()
                .system(systemRules)
                .user(studentMessage)
                .call()
                .content();
    }
}