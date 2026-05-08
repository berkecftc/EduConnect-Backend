package com.educonnect.llmservice.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;

@Service
public class UnifiedAgentService {

    private final ChatClient chatClient;
    private final SemanticCacheService semanticCacheService;

    public UnifiedAgentService(ChatClient.Builder chatClientBuilder, SemanticCacheService semanticCacheService) {
        this.chatClient = chatClientBuilder.build();
        this.semanticCacheService = semanticCacheService;
    }

    /**
     * Unified Agent Chat Endpoint with Semantic Caching.
     * Uses Reactive Programming (Flux) to stream the response.
     */
    public Flux<String> chatWithStream(String userMessage) {
        // 1. Check Semantic Cache
        String cachedResponse = semanticCacheService.getCachedResponse(userMessage);

        if (cachedResponse != null) {
            // Cache HIT: Stream the cached response simulated typing effect
            return streamCachedResponse(cachedResponse);
        }

        // 2. Cache MISS: Call LLM
        // MUST use StringBuffer (Thread-Safe) because Reactive Streams may emit chunks asynchronously across different threads.
        StringBuffer responseBuffer = new StringBuffer();

        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content()
                .doOnNext(responseBuffer::append) // Accumulate stream elements
                .doOnComplete(() -> {
                    // When the stream finishes completely, save the full response to Semantic Cache safely
                    semanticCacheService.putCache(userMessage, responseBuffer.toString());
                });
    }

    /**
     * Helper pattern to stream the cached response word by word for UX.
     */
    private Flux<String> streamCachedResponse(String cachedResponse) {
        String[] words = cachedResponse.split(" ");
        return Flux.fromIterable(Arrays.asList(words))
                .map(word -> word + " ")
                .delayElements(Duration.ofMillis(50)); // Simulate generation UX
    }
}

