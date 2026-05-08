package com.educonnect.llmservice.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ClubRecommendationService {

    private static final Pattern CLUB_NAME_PATTERN =
            Pattern.compile("(?m)^Kul(ü|u)p Ad(ı|i)\\s*:\\s*(.+)$");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}+");
    private static final Set<String> TURKISH_STOPWORDS = Set.of(
            "ve", "veya", "ile", "bir", "bu", "su", "şu", "o", "mi", "mu", "mı", "mü",
            "icin", "için", "gibi", "ama", "ancak", "de", "da", "ki", "ben", "sen",
            "biz", "siz", "onlar", "daha", "cok", "çok", "az", "en", "midir", "mıdır",
            "kulup", "kulüp", "kulub", "kulüb", "kulübü", "kulubu", "topluluk", "topluluğu", "toplulugu",
            "var", "yok", "m", "ilgili", "hakkinda", "hakkında", "buna", "gore", "göre",
            "tavsiye", "tavsiyesi", "ver", "verir", "verebilir", "öneri", "oneri", "istiyorum",
            "hangi", "hangisi", "nelerdir", "ilgileniyorum", "ilgim", "ilgi", "duyuyorum",
            "bana", "sana", "bizi", "sizi", "nedir", "neler",
            "merhaba", "selam", "gunler", "günler", "tesekkurler", "teşekkürler", "ederim",
            "lutfen", "lütfen", "yardim", "yardım", "edebilir", "misiniz", "misin", "mısın", "mısınız",
            "nasil", "nasıl", "kim", "kimler", "dair", "dolayi", "dolayı", "neden", "nicin", "niçin",
            "ne", "zaman", "nerede", "nereye", "nereden", "acaba", "belki", "keske", "keşke",
            "soru", "sorum", "sorabilir", "miyim", "biliyor", "musunuz", "uygun",
            "katilmak", "katılmak", "ariyorum", "arıyorum", "bul", "bulabilir", "yapar",
            "ise", "idi", "imis", "imiş", "dır", "dir", "dur", "dür", "tır", "tir", "tur", "tür",
            "benim", "senin", "onun", "bizim", "sizin", "onlarin", "onların",
            "bize", "size", "onlara", "benden", "senden", "ondan", "bizden", "sizden", "onlardan",
            "olarak", "olan", "olmayan", "olur", "olmaz", "ol", "olsun", "oldukca", "oldukça"
    );
    private static final Set<String> AI_KEYWORDS = Set.of(
            "yapay", "zeka", "ai", "ml", "machine", "learning",
            "makine", "ogrenmesi", "öğrenmesi", "llm", "model", "veri", "data"
    );

    private final VectorStore vectorStore;

    public ClubRecommendationService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String recommendClub(String studentMessage) {

        // 1. Öğrencinin mesajını vektöre çevir ve en benzer 5 kulübü getir
        List<Document> similarClubs = vectorStore.similaritySearch(
                SearchRequest.builder().query(studentMessage).topK(5).similarityThreshold(0.50).build()
        );

        if (similarClubs.isEmpty()) {
            return "Boyle bir kulup yok.";
        }

        // 2. Gelen kulup verilerini metin olarak birlestir
        String context = similarClubs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // Keep context short to reduce prompt size and latency.
        int maxContextChars = 2000;
        if (context.length() > maxContextChars) {
            context = context.substring(0, maxContextChars);
        }

        Set<String> messageTokens = tokenize(studentMessage);
        boolean aiIntent = hasAiIntent(studentMessage, messageTokens);

        List<Document> filteredClubs = similarClubs.stream()
                .filter(doc -> aiIntent
                        ? containsAnyKeyword(doc.getText(), AI_KEYWORDS)
                        : hasKeywordOverlap(doc.getText(), messageTokens))
                .toList();

        if (filteredClubs.isEmpty()) {
            return "Boyle bir kulup yok.";
        }

        List<String> candidateClubNames = filteredClubs.stream()
                .map(Document::getText)
                .map(this::extractClubName)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();

        // If no recognizable club names, return a safe fallback.
        if (candidateClubNames.isEmpty()) {
            return "Boyle bir kulup yok.";
        }

        // Deterministic Turkish response from retrieved data.
        return buildDeterministicReply(filteredClubs);
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (!TURKISH_STOPWORDS.contains(word) && word.length() >= 3) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private boolean hasKeywordOverlap(String content, Set<String> messageTokens) {
        if (content == null || content.isBlank() || messageTokens.isEmpty()) {
            return false;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        for (String token : messageTokens) {
            if (lowerContent.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAiIntent(String studentMessage, Set<String> messageTokens) {
        if (studentMessage == null || studentMessage.isBlank()) {
            return false;
        }
        String lower = studentMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("makine ogrenmesi") || lower.contains("makine öğrenmesi")) {
            return true;
        }
        if (lower.contains("yapay zeka") || lower.contains("artificial intelligence")) {
            return true;
        }
        return containsAnyKeyword(lower, AI_KEYWORDS) ||
                messageTokens.contains("yapay") || messageTokens.contains("zeka");
    }

    private boolean containsAnyKeyword(String content, Set<String> keywords) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lowerContent.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildDeterministicReply(List<Document> similarClubs) {
        List<String> lines = similarClubs.stream()
                .map(Document::getText)
                .map(this::formatClubLine)
                .filter(line -> !line.isBlank())
                .toList();

        if (lines.isEmpty()) {
            return "Boyle bir kulup yok.";
        }

        String header = "Ilgilenebilecegin kulup onerileri:";
        String body = String.join("\n", lines);
        return header + "\n" + body;
    }

    private String formatClubLine(String content) {
        String name = extractClubName(content);
        if (name.isBlank()) {
            return "";
        }
        return String.format("- %s", name);
    }

    private String extractClubName(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        Matcher matcher = CLUB_NAME_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(3).trim();
        }
        String[] lines = content.split("\\R", 2);
        String firstLine = lines.length > 0 ? lines[0].trim() : "";
        if (firstLine.startsWith("Kulüp Adı:")) {
            return firstLine.substring("Kulüp Adı:".length()).trim();
        }
        if (firstLine.startsWith("Kulup Adi:")) {
            return firstLine.substring("Kulup Adi:".length()).trim();
        }
        return firstLine;
    }
}